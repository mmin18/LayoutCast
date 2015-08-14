#!/usr/bin/python

__author__ = 'mmin18'
__version__ = '1.50811'
__plugin__ = '1'

from subprocess import Popen, PIPE, check_call
from distutils.version import LooseVersion
import argparse
import sys
import os
import re
import time
import shutil

# http://stackoverflow.com/questions/377017/test-if-executable-exists-in-python
def which(program):
    import os
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)

    fpath, fname = os.path.split(program)
    if fpath:
        if is_exe(program):
            return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
            path = path.strip('"')
            exe_file = os.path.join(path, program)
            if is_exe(exe_file):
                return exe_file

    return None

def is_gradle_project(dir):
    return os.path.isfile(os.path.join(dir, 'build.gradle'))

def parse_properties(path):
    return os.path.isfile(path) and dict(line.strip().split('=') for line in open(path) if ('=' in line and not line.startswith('#'))) or {}

def balanced_braces(arg):
    if '{' not in arg:
        return ''
    chars = []
    n = 0
    for c in arg:
        if c == '{':
            if n > 0:
                chars.append(c)
            n += 1
        elif c == '}':
            n -= 1
            if n > 0:
                chars.append(c)
            elif n == 0:
                return ''.join(chars).lstrip().rstrip()
        elif n > 0:
            chars.append(c)
    return ''

def remove_comments(str):
    # remove comments in groovy
    return re.sub(r'''(/\*([^*]|[\r\n]|(\*+([^*/]|[\r\n])))*\*+/)|(//.*)''', '', str)

def __deps_list_eclipse(list, project):
    prop = parse_properties(os.path.join(project, 'project.properties'))
    for i in range(1,100):
        dep = prop.get('android.library.reference.%d' % i)
        if dep:
            absdep = os.path.abspath(os.path.join(project, dep))
            __deps_list_eclipse(list, absdep)
            if not absdep in list:
                list.append(absdep)

def __deps_list_gradle(list, project):
    str = ''
    with open(os.path.join(project, 'build.gradle'), 'r') as f:
        str = f.read()
    str = remove_comments(str)
    ideps = []
    # for depends in re.findall(r'dependencies\s*\{.*?\}', str, re.DOTALL | re.MULTILINE):
    for m in re.finditer(r'dependencies\s*\{', str):
        depends = balanced_braces(str[m.start():])
        for proj in re.findall(r'''compile project\(.*['"]:(.+)['"].*\)''', depends):
            ideps.append(proj.replace(':', '/'))
    if len(ideps) == 0:
        return

    path = project
    for i in range(1, 3):
        path = os.path.abspath(os.path.join(path, os.path.pardir))
        b = True
        deps = []
        for idep in ideps:
            dep = os.path.join(path, idep)
            if not os.path.isdir(dep):
                b = False
                break
            deps.append(dep)
        if b:
            for dep in deps:
                __deps_list_gradle(list, dep)
                if not dep in list:
                    list.append(dep)
            break

def deps_list(dir):
    if is_gradle_project(dir):
        list = []
        __deps_list_gradle(list, dir)
        return list
    else:
        list = []
        __deps_list_eclipse(list, dir)
        return list

def manifestpath(dir):
    if os.path.isfile(os.path.join(dir, 'AndroidManifest.xml')):
        return os.path.join(dir, 'AndroidManifest.xml')
    if os.path.isfile(os.path.join(dir, 'src/main/AndroidManifest.xml')):
        return os.path.join(dir, 'src/main/AndroidManifest.xml')

def package_name(dir):
    path = manifestpath(dir)
    if path and os.path.isfile(path):
        with open(path, 'r') as manifestfile:
            data = manifestfile.read()
            return re.findall('package=\"([\w\d_\.]+)\"', data)[0]

def isResName(name):
    if name=='drawable' or name.startswith('drawable-'):
        return 2
    if name=='layout' or name.startswith('layout-'):
        return 2
    if name=='values' or name.startswith('values-'):
        return 2
    if name=='anim' or name.startswith('anim-'):
        return 1
    if name=='color' or name.startswith('color-'):
        return 1
    if name=='menu' or name.startswith('menu-'):
        return 1
    if name=='raw' or name.startswith('raw-'):
        return 1
    if name=='xml' or name.startswith('xml-'):
        return 1
    if name=='mipmap' or name.startswith('mipmap-'):
        return 1
    if name=='animator' or name.startswith('animator-'):
        return 1
    return 0

def countResDir(dir):
    c = 0
    d = 0
    if os.path.isdir(dir):
        for subd in os.listdir(dir):
            v = isResName(subd)
            if v>1:
                d+=1
            if v>0:
                c+=1
    if d==0:
        return 0
    return c

def resdir(dir):
    dir1 = os.path.join(dir, 'res')
    dir2 = os.path.join(dir, 'src/main/res')
    a = countResDir(dir1)
    b = countResDir(dir2)
    if b==0 and a==0:
        return None
    elif b>a:
        return dir2
    else:
        return dir1

def countSrcDir2(dir, lastBuild=0, list=None):
    count = 0
    lastModified = 0
    for dirpath, dirnames, files in os.walk(dir):
        if '/androidTest/' in dirpath or '/.' in dirpath:
            continue
        for fn in files:
            if fn.endswith('.java'):
                count += 1
                mt = os.path.getmtime(os.path.join(dirpath, fn))
                lastModified = max(lastModified, mt)
                if list!=None and mt>lastBuild:
                    list.append(os.path.join(dirpath, fn))
    return (count, lastModified)

def srcdir2(dir, lastBuild=0, list=None):
    dir1 = os.path.join(dir, 'src')
    dir2 = os.path.join(dir, 'src/main/src')
    list1 = None
    list2 = None
    if list!=None:
        list1 = []
        list2 = []
    (a, ma) = countSrcDir2(dir1, lastBuild=lastBuild, list=list1)
    (b, mb) = countSrcDir2(dir2, lastBuild=lastBuild, list=list2)
    if b>a:
        if list!=None:
            list.extend(list2)
        return (dir2, b, mb)
    else:
        if list!=None:
            list.extend(list1)
        return (dir1, a, ma)

def libdir(dir):
    ddir = os.path.join(dir, 'libs')
    if os.path.isdir(ddir):
        return ddir
    else:
        return None

def is_launchable_project(dir):
    if is_gradle_project(dir):
        with open(os.path.join(dir, 'build.gradle'), 'r') as buildfile:
            data = buildfile.read()
            data = remove_comments(data)
            if re.findall(r'''apply\s+plugin:\s*['"]com.android.application['"]''', data, re.MULTILINE):
                return True
    elif os.path.isfile(os.path.join(dir, 'project.properties')):
        with open(os.path.join(dir, 'project.properties'), 'r') as propfile:
            data = propfile.read()
            if re.findall(r'''^\s*target\s*=.*$''', data, re.MULTILINE) and not re.findall(r'''^\s*android.library\s*=\s*true\s*$''', data, re.MULTILINE):
                return True
    return False

def __append_project(list, dir, depth):
    if package_name(dir):
        list.append(dir)
    elif depth > 0:
        for cname in os.listdir(dir):
            if cname=='build' or cname=='bin':
                continue
            cdir = os.path.join(dir, cname)
            if os.path.isdir(cdir):
                __append_project(list, cdir, depth-1)

def list_projects(dir):
    list = []
    if os.path.isfile(os.path.join(dir, 'settings.gradle')):
        with open(os.path.join(dir, 'settings.gradle'), 'r') as f:
            data = f.read()
            for line in re.findall(r'''include\s*(.+)''', data):
                for proj in re.findall(r'''[\s,]+['"](.*?)['"]''', ','+line):
                    dproj = (proj.startswith(':') and proj[1:] or proj).replace(':', '/')
                    cdir = os.path.join(dir, dproj)
                    if package_name(cdir):
                        list.append(cdir)
    else:
        __append_project(list, dir, 2)
    return list

def list_aar_projects(dir, deps):
    pnlist = [package_name(i) for i in deps]
    pnlist.append(package_name(dir))
    list1 = []
    if os.path.isdir(os.path.join(dir, 'build/intermediates/incremental/mergeResources')):
        for dirpath, dirnames, files in os.walk(os.path.join(dir, 'build/intermediates/incremental/mergeResources')):
            if '/androidTest/' in dirpath:
                continue
            for fn in files:
                if fn=='merger.xml':
                    with open(os.path.join(dirpath, fn), 'r') as f:
                        data = f.read()
                        for ppath in re.findall(r'''path="([^"]*?/res)"''', data):
                            if not ppath in list1:
                                list1.append(ppath)
    list2 = []
    for ppath in list1:
        parpath = os.path.abspath(os.path.join(ppath, os.pardir))
        pn = package_name(parpath)
        if pn and not pn in pnlist:
            list2.append(ppath)
    return list2

def cexec(args, failOnError = True):
    p = Popen(args, stdin=PIPE, stdout=PIPE, stderr=PIPE)
    output, err = p.communicate()
    if failOnError and p.returncode != 0:
        print('Fail to exec %s'%args)
        print(output)
        print(err)
        exit(1)
    return output

def get_android_jar(path):
    if not os.path.isdir(path):
        return None
    platforms = os.path.join(path, 'platforms')
    if not os.path.isdir(platforms):
        return None
    api = 0
    result = None
    for pd in os.listdir(platforms):
        pd = os.path.join(platforms, pd)
        if os.path.isdir(pd) and os.path.isfile(os.path.join(pd, 'source.properties')) and os.path.isfile(os.path.join(pd, 'android.jar')):
            with open(os.path.join(pd, 'source.properties'), 'r') as f:
                s = f.read()
                m = re.search(r'^AndroidVersion.ApiLevel\s*[=:]\s*(.*)$', s, re.MULTILINE)
                if m:
                    a = int(m.group(1))
                    if a > api:
                        api = a
                        result = os.path.join(pd, 'android.jar')
    return result

def get_adb(path):
    if os.path.isdir(path) and os.path.isfile(os.path.join(path, 'platform-tools/adb')):
        return os.path.join(path, 'platform-tools/adb')

def get_aapt(path):
    if os.path.isdir(path) and os.path.isdir(os.path.join(path, 'build-tools')):
        btpath = os.path.join(path, 'build-tools')
        minv = LooseVersion('0')
        minp = None
        for pn in os.listdir(btpath):
            if os.path.isfile(os.path.join(btpath, pn, 'aapt')):
                if LooseVersion(pn) > minv:
                    minp = os.path.join(btpath, pn, 'aapt')
        return minp

def get_dx(path):
    if os.path.isdir(path) and os.path.isdir(os.path.join(path, 'build-tools')):
        btpath = os.path.join(path, 'build-tools')
        minv = LooseVersion('0')
        minp = None
        for pn in os.listdir(btpath):
            if os.path.isfile(os.path.join(btpath, pn, 'dx')):
                if LooseVersion(pn) > minv:
                    minp = os.path.join(btpath, pn, 'dx')
        return minp

def get_android_sdk(dir, condf = get_android_jar):
    if os.path.isfile(os.path.join(dir, 'local.properties')):
        with open(os.path.join(dir, 'local.properties'), 'r') as f:
            s = f.read()
            m = re.search(r'^sdk.dir\s*[=:]\s*(.*)$', s, re.MULTILINE)
            if m and os.path.isdir(m.group(1)) and condf(m.group(1)):
                return m.group(1)

    path = os.getenv('ANDROID_HOME')
    if path and os.path.isdir(path) and condf(path):
        return path

    path = os.getenv('ANDROID_SDK')
    if path and os.path.isdir(path) and condf(path):
        return path

    path = os.path.expanduser('~/Library/Android/sdk')
    if path and os.path.isdir(path) and condf(path):
        return path

    path = '/Applications/android-sdk-mac_86'
    if path and os.path.isdir(path) and condf(path):
        return path

    path = '/android-sdk-mac_86'
    if path and os.path.isdir(path) and condf(path):
        return path

def search_path(dir, filename):
    dir0 = filename
    if '/' in filename:
        dir0 = filename[0:filename.index('/')]
    list = []
    for dirpath, dirnames, files in os.walk(dir):
        if '/androidTest/' in dirpath or '/.' in dirpath:
            continue
        if dir0 in dirnames and os.path.isfile(os.path.join(dirpath, filename)):
            list.append(dirpath)    
    if len(list) == 1:
        return list[0]
    elif len(list) > 1:
        maxt = 0
        maxd = None
        for ddir in list:
            lastModified = 0
            for dirpath, dirnames, files in os.walk(dir):
                for fn in files:
                    if fn.endswith('.class'):
                        lastModified = os.path.getmtime(os.path.join(dirpath, fn))
            if lastModified > maxt:
                maxt = lastModified
                maxd = ddir
        return maxd
    elif os.path.exists(os.path.join(dir, 'common')):
        return os.path.join(dir, 'common','debug')
    else:    
        return os.path.join(dir, 'debug')

if __name__ == "__main__":

    dir = '.'
    sdkdir = None

    starttime = time.time()

    if len(sys.argv) > 1:
        parser = argparse.ArgumentParser()
        parser.add_argument('--sdk', help='specify Android SDK path')
        parser.add_argument('project')
        args = parser.parse_args()
        if args.sdk:
            sdkdir = args.sdk
        if args.project:
            dir = args.project

    if not which('curl'):
        print('curl is required')
        exit(1)

    projlist = [i for i in list_projects(dir) if is_launchable_project(i)]

    if not projlist:
        print('no valid android project found in '+os.path.abspath(dir))
        exit(1)

    pnlist = [package_name(i) for i in projlist]
    portlist = [0 for i in pnlist]
    stlist = [-1 for i in pnlist]

    if not sdkdir:
        sdkdir = get_android_sdk(dir)
        if not sdkdir:
            print('android sdk not found, specify in local.properties or export ANDROID_HOME')
            exit(1)

    adbpath = get_adb(sdkdir)
    if not adbpath:
        print('adb not found in %s/platform-tools'%sdkdir)
        exit(1)
    for i in range(0, 10):
        cexec([adbpath, 'forward', 'tcp:%d'%(41128+i), 'tcp:%d'%(41128+i)])
        output = cexec(['curl', 'http://127.0.0.1:%d/packagename'%(41128+i)], failOnError = False).strip()
        if output and output in pnlist:
            ii=pnlist.index(output)
            output = cexec(['curl', 'http://127.0.0.1:%d/appstate'%(41128+i)], failOnError=False).strip()
            if output and int(output) > stlist[ii]:
                portlist[ii] = (41128+i)
                stlist[ii] = int(output)

    maxst = max(stlist)
    port=0
    if maxst == -1:
        print('package %s not found, make sure your project is properly setup and running'%(len(pnlist)==1 and pnlist[0] or pnlist))
    elif stlist.count(maxst) > 1:
        alist = [pnlist[i] for i in range(0, len(pnlist)) if stlist[i] >= 0]
        print('multiple packages %s running%s'%(alist, (maxst==2 and '.' or ', keep one of your application visible and cast again')))
    else:
        i = stlist.index(maxst)
        port = portlist[i]
        dir = projlist[i]
        packagename = pnlist[i]
    for i in range(0, 10):
        if (41128+i) != port:
            cexec([adbpath, 'forward', '--remove', 'tcp:%d'%(41128+i)], failOnError=False)
    if port==0:
        exit(1)

    is_gradle = is_gradle_project(dir)

    android_jar = get_android_jar(sdkdir)
    if not android_jar:
        print('android.jar not found !!!\nUse local.properties or set ANDROID_HOME env')

    deps = deps_list(dir)
    bindir = os.path.join(dir, is_gradle and 'build/lcast' or 'bin/lcast')

    # check if the /res and /src has changed
    lastBuild = 0
    rdir = is_gradle and os.path.join(dir, 'build', 'outputs', 'apk') or os.path.join(dir, 'bin')
    if os.path.isdir(rdir):
        for fn in os.listdir(rdir):
            if fn.endswith('.apk') and not '-androidTest' in fn:
                fpath = os.path.join(rdir, fn)
                lastBuild = os.path.getmtime(fpath)
    adeps = []
    adeps.extend(deps)
    adeps.append(dir)
    latestResModified = 0
    latestSrcModified = 0
    srcs = []
    msrclist = []
    for dep in adeps:
        rdir = resdir(dep)
        if rdir:
            for subd in os.listdir(rdir):
                if os.path.isdir(os.path.join(rdir, subd)) and isResName(subd):
                    for fn in os.listdir(os.path.join(rdir, subd)):
                        fpath = os.path.join(rdir, subd, fn)
                        if os.path.isfile(fpath) and not fn.startswith('.'):
                            latestResModified = max(latestResModified, os.path.getmtime(fpath))
        (sdir, scount, smt) = srcdir2(dep, lastBuild=lastBuild, list=msrclist)
        srcs.append(sdir)
        latestSrcModified = max(latestSrcModified, smt)
    resModified = latestResModified > lastBuild
    srcModified = latestSrcModified > lastBuild
    targets = ''
    if resModified and srcModified:
        targets = 'both /res and /src'
    elif resModified:
        targets = '/res'
    elif srcModified:
        targets = '/src'
    else:
        print('%s has no /res or /src changes'%(packagename))
        exit(0)

    if is_gradle:
        print('cast %s:%d as gradle project with %s changed'%(packagename, port, targets))
    else:
        print('cast %s:%d as eclipse project with %s changed'%(packagename, port, targets))

    # prepare to reset
    if srcModified:
        cexec(['curl', 'http://127.0.0.1:%d/pcast'%port], failOnError=False)

    if resModified:
        binresdir = os.path.join(bindir, 'res')
        if not os.path.exists(os.path.join(binresdir, 'values')):
            os.makedirs(os.path.join(binresdir, 'values'))

        cexec(['curl', '--silent', '--output', os.path.join(binresdir, 'values/ids.xml'), 'http://127.0.0.1:%d/ids.xml'%port])
        cexec(['curl', '--silent', '--output', os.path.join(binresdir, 'values/public.xml'), 'http://127.0.0.1:%d/public.xml'%port])

        aaptpath = get_aapt(sdkdir)
        if not aaptpath:
            print('aapt not found in %s/build-tools'%sdkdir)
            exit(1)
        aaptargs = [aaptpath, 'package', '-f', '--auto-add-overlay', '-F', os.path.join(bindir, 'res.zip')]
        if is_gradle:
            for dep in list_aar_projects(dir, deps):
                aaptargs.append('-S')
                aaptargs.append(dep)
        
        rdir = resdir(dir)
        if rdir:
            aaptargs.append('-S')
            aaptargs.append(rdir)
        aaptargs.append('-S')
        aaptargs.append(binresdir)
        aaptargs.append('-M')
        aaptargs.append(manifestpath(dir))
        aaptargs.append('-I')
        aaptargs.append(android_jar)
        for dep in deps:
            rdir = resdir(dep)
            if rdir:
                aaptargs.append('-S')
                aaptargs.append(rdir)
        cexec(aaptargs)

        cexec(['curl', '--silent', '-T', os.path.join(bindir, 'res.zip'), 'http://127.0.0.1:%d/pushres'%port])

    if srcModified:
        vmversion = cexec(['curl', 'http://127.0.0.1:%d/vmversion'%port], failOnError=False)
        if vmversion.startswith('1'):
            print('cast dex to dalvik vm is not supported, you need ART in Android 5.0')
        elif vmversion.startswith('2'):
            if not which('javac'):
                print('javac is required to compile java code, config your PATH to include javac')

            launcher = cexec(['curl', 'http://127.0.0.1:%d/launcher'%port])

            classpath = [android_jar]
            for dep in adeps:
                dlib = libdir(dep)
                if dlib:
                    for fjar in os.listdir(dlib):
                        if fjar.endswith('.jar'):
                            classpath.append(os.path.join(dlib, fjar))
            if is_gradle:
                darr = os.path.join(dir, 'build', 'intermediates', 'exploded-aar')
                # TODO: use the max version
                for dirpath, dirnames, files in os.walk(darr):
                    if '/androidTest/' in dirpath or '/.' in dirpath:
                        continue
                    for fn in files:
                        if fn.endswith('.jar'):
                            classpath.append(os.path.join(dirpath, fn));
                        
                # R.class
                classesdir = search_path(os.path.join(dir, 'build', 'intermediates', 'classes'), launcher and launcher.replace('.', '/')+'.class' or '$')
                classpath.append(classesdir)
            else:
                # R.class
                classpath.append(os.path.join(dir, 'bin', 'classes'))

            binclassesdir = os.path.join(bindir, 'classes')
            shutil.rmtree(binclassesdir, ignore_errors=True)
            os.makedirs(binclassesdir)

            javacargs = ['javac', '-target', '1.7', '-source', '1.7']
            javacargs.append('-cp')
            javacargs.append(':'.join(classpath))
            javacargs.append('-d')
            javacargs.append(binclassesdir)
            javacargs.append('-sourcepath')
            javacargs.append(':'.join(srcs))
            javacargs.extend(msrclist)
            cexec(javacargs)

            dxpath = get_dx(sdkdir)
            if not dxpath:
                print('dx not found in %s/build-tools'%sdkdir)
                exit(1)
            dxoutput = os.path.join(bindir, 'classes.dex')
            if os.path.isfile(dxoutput):
                os.remove(dxoutput)
            cexec([dxpath, '--dex', '--output=%s'%dxoutput, binclassesdir])

            cexec(['curl', '--silent', '-T', dxoutput, 'http://127.0.0.1:%d/pushdex'%port])

        else:
            if is_gradle:
                print('LayoutCast library out of date, please sync your project with gradle')
            else:
                print('libs/lcast.jar is out of date, please update')

    cexec(['curl', '--silent', 'http://127.0.0.1:%d/lcast'%port])

    cexec([adbpath, 'forward', '--remove', 'tcp:%d'%port], failOnError=False)

    elapsetime = time.time() - starttime
    print('finished in %dms'%(elapsetime*1000))
