#!/usr/bin/python

__author__ = 'mmin18'
__version__ = '1.50816'
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
def is_exe(fpath):
    return os.path.isfile(fpath) and os.access(fpath, os.X_OK)
def which(program):
    import os
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

def cexec(args, failOnError = True, addPath = None):
    env = None
    if addPath:
        import copy
        env = copy.copy(os.environ)
        env['PATH'] = addPath + os.path.pathsep + env['PATH']
    p = Popen(args, stdin=PIPE, stdout=PIPE, stderr=PIPE, env=env)
    output, err = p.communicate()
    if failOnError and p.returncode != 0:
        print('Fail to exec %s'%args)
        print(output)
        print(err)
        exit(1)
    return output

def curl(url, body=None, ignoreError=False):
    import sys
    try:
        if sys.version_info >= (3, 0):
            import urllib.request
            return urllib.request.urlopen(url, data=body).read().decode('utf-8').strip()
        else:
            import urllib2
            return urllib2.urlopen(url, data=body).read().decode('utf-8').strip()
    except Exception as e:
        if ignoreError:
            return None
        else:
            print(e)
            exit(-1)

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
            ideps.append(proj.replace(':', os.path.sep))
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
    if os.path.isfile(os.path.join(dir, 'src', 'main', 'AndroidManifest.xml')):
        return os.path.join(dir, 'src', 'main', 'AndroidManifest.xml')

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
    dir2 = os.path.join(dir, 'src', 'main', 'res')
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
        if re.findall(r'[/\\+]androidTest[/\\+]', dirpath) or '/.' in dirpath:
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
    for srcdir in [os.path.join(dir, 'src', 'main', 'java'), os.path.join(dir, 'src')]:
        olist = None
        if list!=None:
            olist = []
        (count, lastModified) = countSrcDir2(srcdir, lastBuild=lastBuild, list=olist)
        if count>0:
            if list!=None:
                list.extend(olist)
            return (srcdir, count, lastModified)
    return (None, 0, 0)

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
                    dproj = (proj.startswith(':') and proj[1:] or proj).replace(':', os.path.sep)
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
    if os.path.isdir(os.path.join(dir, 'build', 'intermediates', 'incremental', 'mergeResources')):
        for dirpath, dirnames, files in os.walk(os.path.join(dir, 'build', 'intermediates', 'incremental', 'mergeResources')):
            if re.findall(r'[/\\+]androidTest[/\\+]', dirpath):
                continue
            for fn in files:
                if fn=='merger.xml':
                    with open(os.path.join(dirpath, fn), 'r') as f:
                        data = f.read()
                        for ppath in re.findall(r'''path="([^"]*?[/\\+]res)"''', data):
                            if not ppath in list1:
                                list1.append(ppath)
    list2 = []
    for ppath in list1:
        parpath = os.path.abspath(os.path.join(ppath, os.pardir))
        pn = package_name(parpath)
        if pn and not pn in pnlist:
            list2.append(ppath)
    return list2

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
    execname = os.name=='nt' and 'adb.exe' or 'adb'
    if os.path.isdir(path) and is_exe(os.path.join(path, 'platform-tools', execname)):
        return os.path.join(path, 'platform-tools', execname)

def get_aapt(path):
    execname = os.name=='nt' and 'aapt.exe' or 'aapt'
    if os.path.isdir(path) and os.path.isdir(os.path.join(path, 'build-tools')):
        btpath = os.path.join(path, 'build-tools')
        minv = LooseVersion('0')
        minp = None
        for pn in os.listdir(btpath):
            if is_exe(os.path.join(btpath, pn, execname)):
                if LooseVersion(pn) > minv:
                    minv = LooseVersion(pn)
                    minp = os.path.join(btpath, pn, execname)
        return minp

def get_dx(path):
    execname = os.name=='nt' and 'dx.bat' or 'dx'
    if os.path.isdir(path) and os.path.isdir(os.path.join(path, 'build-tools')):
        btpath = os.path.join(path, 'build-tools')
        minv = LooseVersion('0')
        minp = None
        for pn in os.listdir(btpath):
            if is_exe(os.path.join(btpath, pn, execname)):
                if LooseVersion(pn) > minv:
                    minv = LooseVersion(pn)
                    minp = os.path.join(btpath, pn, execname)
        return minp

def get_android_sdk(dir, condf = get_android_jar):
    if os.path.isfile(os.path.join(dir, 'local.properties')):
        with open(os.path.join(dir, 'local.properties'), 'r') as f:
            s = f.read()
            m = re.search(r'^sdk.dir\s*[=:]\s*(.*)$', s, re.MULTILINE)
            if m:
                val = m.group(1).replace('\\:', ':').replace('\\=', '=').replace('\\\\', '\\')
                if os.path.isdir(val) and condf(val):
                    return val

    path = os.getenv('ANDROID_HOME')
    if path and os.path.isdir(path) and condf(path):
        return path

    path = os.getenv('ANDROID_SDK')
    if path and os.path.isdir(path) and condf(path):
        return path

    # mac
    path = os.path.expanduser('~/Library/Android/sdk')
    if path and os.path.isdir(path) and condf(path):
        return path

    # windows
    path = os.path.expanduser('~/AppData/Local/Android/sdk')
    if path and os.path.isdir(path) and condf(path):
        return path

def get_javac(dir):
    execname = os.name=='nt' and 'javac.exe' or 'javac'
    if dir and os.path.isfile(os.path.join(dir, 'bin', execname)):
        return os.path.join(dir, 'bin', execname)

    wpath = which(execname)
    if wpath:
        return wpath

    path = os.getenv('JAVA_HOME')
    if path and is_exe(os.path.join(path, 'bin', execname)):
        return os.path.join(path, 'bin', execname)

    if os.name=='nt':
        btpath = 'C:\\Program Files\\Java'
        if os.path.isdir(btpath):
            minv = ''
            minp = None
            for pn in os.listdir(btpath):
                path = os.path.join(btpath, pn, 'bin', execname)
                if is_exe(path):
                    if pn > minv:
                        minv = pn
                        minp = path
            return minp
    else:
        for btpath in ['/Library/Java/JavaVirtualMachines', '/System/Library/Java/JavaVirtualMachines']:
            if os.path.isdir(btpath):
                minv = ''
                minp = None
                for pn in os.listdir(btpath):
                    path = os.path.join(btpath, pn, 'Contents', 'Home', 'bin', execname)
                    if is_exe(path):
                        if pn > minv:
                            minv = pn
                            minp = path
                if minp:
                    return minp

def search_path(dir, filename):
    dir0 = filename
    if os.path.sep in filename:
        dir0 = filename[0:filename.index(os.path.sep)]
    list = []
    for dirpath, dirnames, files in os.walk(dir):
        if re.findall(r'[/\\+]androidTest[/\\+]', dirpath) or '/.' in dirpath:
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
    else:
        return os.path.join(dir, 'debug')

if __name__ == "__main__":

    dir = '.'
    sdkdir = None
    jdkdir = None

    starttime = time.time()

    if len(sys.argv) > 1:
        parser = argparse.ArgumentParser()
        parser.add_argument('--sdk', help='specify Android SDK path')
        parser.add_argument('--jdk', help='specify JDK path')
        parser.add_argument('project')
        args = parser.parse_args()
        if args.sdk:
            sdkdir = args.sdk
        if args.jdk:
            jdkdir = args.jdk
        if args.project:
            dir = args.project

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
        output = curl('http://127.0.0.1:%d/packagename'%(41128+i), ignoreError=True)
        if output and output in pnlist:
            ii=pnlist.index(output)
            output = curl('http://127.0.0.1:%d/appstate'%(41128+i), ignoreError=True)
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
    bindir = is_gradle and os.path.join(dir, 'build', 'lcast') or os.path.join(dir, 'bin', 'lcast')

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
        if sdir:
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
        print('cast %s:%d as gradle project with %s changed (v%s)'%(packagename, port, targets, __version__))
    else:
        print('cast %s:%d as eclipse project with %s changed (v%s)'%(packagename, port, targets, __version__))

    # prepare to reset
    if srcModified:
        curl('http://127.0.0.1:%d/pcast'%port, ignoreError=True)

    if resModified:
        binresdir = os.path.join(bindir, 'res')
        if not os.path.exists(os.path.join(binresdir, 'values')):
            os.makedirs(os.path.join(binresdir, 'values'))

        data = curl('http://127.0.0.1:%d/ids.xml'%port)
        with open(os.path.join(binresdir, 'values/ids.xml'), 'w') as fp:
            fp.write(data)
        data = curl('http://127.0.0.1:%d/public.xml'%port)
        with open(os.path.join(binresdir, 'values/public.xml'), 'w') as fp:
            fp.write(data)

        aaptpath = get_aapt(sdkdir)
        if not aaptpath:
            print('aapt not found in %s/build-tools'%sdkdir)
            exit(1)
        aaptargs = [aaptpath, 'package', '-f', '--auto-add-overlay', '-F', os.path.join(bindir, 'res.zip')]
        aaptargs.append('-S')
        aaptargs.append(binresdir)
        rdir = resdir(dir)
        if rdir:
            aaptargs.append('-S')
            aaptargs.append(rdir)
        for dep in reversed(deps):
            rdir = resdir(dep)
            if rdir:
                aaptargs.append('-S')
                aaptargs.append(rdir)
        if is_gradle:
            for dep in reversed(list_aar_projects(dir, deps)):
                aaptargs.append('-S')
                aaptargs.append(dep)
        aaptargs.append('-M')
        aaptargs.append(manifestpath(dir))
        aaptargs.append('-I')
        aaptargs.append(android_jar)
        cexec(aaptargs)

        with open(os.path.join(bindir, 'res.zip'), 'rb') as fp:
            curl('http://127.0.0.1:%d/pushres'%port, body=fp.read())

    if srcModified:
        vmversion = curl('http://127.0.0.1:%d/vmversion'%port, ignoreError=True)
        if vmversion==None:
            vmversion = ''
        if vmversion.startswith('1'):
            print('cast dex to dalvik vm is not supported, you need ART in Android 5.0')
        elif vmversion.startswith('2'):
            javac = get_javac(jdkdir)
            if not javac:
                print('javac is required to compile java code, config your PATH to include javac')
                exit(-1)

            launcher = curl('http://127.0.0.1:%d/launcher'%port)

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
                    if re.findall(r'[/\\+]androidTest[/\\+]', dirpath) or '/.' in dirpath:
                        continue
                    for fn in files:
                        if fn.endswith('.jar'):
                            classpath.append(os.path.join(dirpath, fn))
                # R.class
                classesdir = search_path(os.path.join(dir, 'build', 'intermediates', 'classes'), launcher and launcher.replace('.', os.path.sep)+'.class' or '$')
                classpath.append(classesdir)
            else:
                # R.class
                classpath.append(os.path.join(dir, 'bin', 'classes'))

            binclassesdir = os.path.join(bindir, 'classes')
            shutil.rmtree(binclassesdir, ignore_errors=True)
            os.makedirs(binclassesdir)

            javacargs = [javac, '-target', '1.7', '-source', '1.7']
            javacargs.append('-cp')
            javacargs.append(os.pathsep.join(classpath))
            javacargs.append('-d')
            javacargs.append(binclassesdir)
            javacargs.append('-sourcepath')
            javacargs.append(os.pathsep.join(srcs))
            javacargs.extend(msrclist)
            cexec(javacargs)

            dxpath = get_dx(sdkdir)
            if not dxpath:
                print('dx not found in %s/build-tools'%sdkdir)
                exit(1)
            dxoutput = os.path.join(bindir, 'classes.dex')
            if os.path.isfile(dxoutput):
                os.remove(dxoutput)
            addPath = None
            if os.name == 'nt':
                # fix system32 java.exe issue
                addPath = os.path.abspath(os.path.join(javac, os.pardir))
            cexec([dxpath, '--dex', '--output=%s'%dxoutput, binclassesdir], addPath = addPath)

            with open(dxoutput, 'rb') as fp:
                curl('http://127.0.0.1:%d/pushdex'%port, body=fp.read())

        else:
            if is_gradle:
                print('LayoutCast library out of date, please sync your project with gradle')
            else:
                print('libs/lcast.jar is out of date, please update')

    curl('http://127.0.0.1:%d/lcast'%port)

    cexec([adbpath, 'forward', '--remove', 'tcp:%d'%port], failOnError=False)

    elapsetime = time.time() - starttime
    print('finished in %dms'%(elapsetime*1000))
