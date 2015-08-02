#!/usr/bin/python

__author__ = 'mmin18'

from subprocess import Popen, PIPE, check_call
import sys
import os
import re

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

def countResDir(dir):
    c = 0
    d = 0
    if os.path.isdir(dir):
        for subd in os.listdir(dir):
            if subd=='drawable' or subd.startswith('drawable-'):
                c+=1
                d+=1
            if subd=='layout' or subd.startswith('layout-'):
                c+=1
                d+=1
            if subd=='values' or subd.startswith('values-'):
                c+=1
                d+=1
            if subd=='anim' or subd.startswith('anim-'):
                c+=1
            if subd=='color' or subd.startswith('color-'):
                c+=1
            if subd=='menu' or subd.startswith('menu-'):
                c+=1
            if subd=='raw' or subd.startswith('raw-'):
                c+=1
            if subd=='xml' or subd.startswith('xml-'):
                c+=1
            if subd=='mipmap' or subd.startswith('mipmap-'):
                c+=1
            if subd=='animator' or subd.startswith('animator-'):
                c+=1
    if d==0:
        return 0
    return c

def resdir(dir):
    dir1 = os.path.join(dir, 'res')
    dir2 = os.path.join(dir, 'src/main/res')
    a = countResDir(dir1)
    b = countResDir(dir2)
    if a>0 or b>0:
        return a>b and dir1 or dir2

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

def find_android_jar(dir):
    if os.path.isfile(os.path.join(dir, 'local.properties')):
        with open(os.path.join(dir, 'local.properties'), 'r') as f:
            s = f.read()
            m = re.search(r'^sdk.dir\s*[=:]\s*(.*)$', s, re.MULTILINE)
            if m:
                r = get_android_jar(m.group(1))
                if r:
                    return r

    if os.getenv('ANDROID_HOME'):
        r = get_android_jar(os.getenv('ANDROID_HOME'))
        if r:
            return r

    if os.getenv('ANDROID_SDK'):
        r = get_android_jar(os.getenv('ANDROID_SDK'))
        if r:
            return r

    r = get_android_jar(os.path.expanduser('~/Library/Android/sdk'))
    if r:
        return r

    r = get_android_jar('/Applications/android-sdk-mac_86')
    if r:
        return r

    r = get_android_jar('/android-sdk-mac_86')
    if r:
        return r

if __name__ == "__main__":

    dir = '.'
    if len(sys.argv) > 1:
        dir = sys.argv[1]

    projlist = [i for i in list_projects(dir) if is_launchable_project(i)]

    if not projlist:
        print('no valid android project found in '+os.path.abspath(dir))
        exit(1)

    pnlist = [package_name(i) for i in projlist]
    portlist = [0 for i in pnlist]
    stlist = [-1 for i in pnlist]

    for i in range(0, 32):
        cexec(['adb', 'forward', 'tcp:%d'%(41128+i), 'tcp:%d'%(41128+i)])
        output = cexec(['curl', 'http://127.0.0.1:%d/packagename'%(41128+i)], failOnError = False).strip()
        if output and output in pnlist:
            ii=pnlist.index(output)
            portlist[ii] = (41128+i)
            output = cexec(['curl', 'http://127.0.0.1:%d/appstate'%(41128+i)], failOnError=False).strip()
            stlist[ii] = output and int(output) or -1

    maxst = max(stlist)
    port=41128
    if maxst == -1:
        print('package %s not found, make sure your project is properly setup and running'%pnlist)
        exit(1)
    elif stlist.count(maxst) > 1:
        print('multiple packages %s running'%pnlist + (maxst==2 and '' or ', keep one of your application visible and cast again'))
        exit(1)
    else:
        i = stlist.index(maxst)
        port = portlist[i]
        dir = projlist[i]
        packagename = pnlist[i]
        for i in range(0, 32):
            if (41128+i) != port:
                cexec(['adb', 'forward', '--remove', 'tcp:%d'%(41128+i)], failOnError=False)

    is_gradle = is_gradle_project(dir)
    if is_gradle:
        print('cast '+packagename+' as gradle project')
    else:
        print('cast '+packagename+' as eclipse project')

    android_jar = find_android_jar(dir)
    if not android_jar:
        print('android.jar not found !!!\nUse local.properties or set ANDROID_HOME env')

    bindir = os.path.join(dir, is_gradle and 'build/lcast' or 'bin/lcast')
    binresdir = os.path.join(bindir, 'res')
    if not os.path.exists(os.path.join(binresdir, 'values')):
        os.makedirs(os.path.join(binresdir, 'values'))

    cexec(['curl', '--silent', '--output', os.path.join(binresdir, 'values/ids.xml'), 'http://127.0.0.1:%d/ids.xml'%port])
    cexec(['curl', '--silent', '--output', os.path.join(binresdir, 'values/public.xml'), 'http://127.0.0.1:%d/public.xml'%port])

    aaptargs = ['aapt', 'package', '-f', '--auto-add-overlay', '-F', os.path.join(bindir, 'res.zip')]
    if is_gradle:
        intermediatesRes = os.path.join(dir, 'build/intermediates/res/debug')
        if countResDir(intermediatesRes) > 1:
            aaptargs.append('-S')
            aaptargs.append(intermediatesRes)
        else:
            print('intermediates res not found under build dir, better keep intermediates after gradle finish build')
    for dep in deps_list(dir):
        aaptargs.append('-S')
        aaptargs.append(resdir(dep))
    aaptargs.append('-S')
    aaptargs.append(resdir(dir))
    aaptargs.append('-S')
    aaptargs.append(binresdir)
    aaptargs.append('-M')
    aaptargs.append(os.path.join(dir, 'AndroidManifest.xml'))
    aaptargs.append('-I')
    aaptargs.append(android_jar)
    cexec(aaptargs)

    print('upload and cast..')
    cexec(['curl', '--silent', '-T', os.path.join(bindir, 'res.zip'), 'http://127.0.0.1:%d/pushres'%41128])
    cexec(['curl', '--silent', 'http://127.0.0.1:%d/lcast'%41128])
