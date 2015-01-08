#!/usr/bin/python

__author__ = 'mmin18'

from subprocess import Popen, PIPE, check_call
import os
import re

def parse_properties(path):
    return os.path.isfile(path) and dict(line.strip().split('=') for line in open(path) if ('=' in line and not line.startswith('#'))) or {}

def __deps_list(list, project):
    prop = parse_properties(os.path.join(project, 'project.properties'))
    for i in range(1,100):
        dep = prop.get('android.library.reference.%d' % i)
        if dep:
            absdep = os.path.abspath(os.path.join(project, dep))
            __deps_list(list, absdep)
            list.append(absdep)

def deps_list():
    list = []
    __deps_list(list, '.')
    return list

def package_name():
    path = 'AndroidManifest.xml'
    if os.path.isfile(path):
        with open(path, 'r') as manifestfile:
            data = manifestfile.read()
            return re.findall('package=\"([\w\d_\.]+)\"', data)[0]

pn = package_name();
for i in range(0, 100):
    check_call(['adb', 'forward', 'tcp:41128', 'tcp:%d'%(41128+i)], stdin=PIPE, stdout=PIPE, stderr=PIPE)
    p = Popen(['curl', 'http://127.0.0.1:41128/packagename'], stdin=PIPE, stdout=PIPE, stderr=PIPE)
    output, err = p.communicate()
    if p.returncode != 0:
        exit(1)
    output.strip()
    if output == pn:
        print('found package '+pn+' at port %d'%(41128+i))
        break

if not os.path.exists('bin/lcast/values'):
    os.makedirs('bin/lcast/values')

check_call(['curl', '--silent', '--output', 'bin/lcast/values/ids.xml', 'http://127.0.0.1:41128/ids.xml'])
check_call(['curl', '--silent', '--output', 'bin/lcast/values/public.xml', 'http://127.0.0.1:41128/public.xml'])

aaptargs = ['aapt', 'package', '-f', '--auto-add-overlay', '-F', 'bin/res.zip', '-S', 'bin/lcast']
for dep in deps_list():
    aaptargs.append('-S')
    aaptargs.append(os.path.join(dep, 'res'))
aaptargs.append('-S')
aaptargs.append('res')
aaptargs.append('-M')
aaptargs.append('AndroidManifest.xml')
aaptargs.append('-I')
aaptargs.append('/Applications/android-sdk-mac_86/platforms/android-19/android.jar')
check_call(aaptargs)

print('upload and cast..')
check_call(['curl', '--silent', '-T', 'bin/res.zip', 'http://localhost:41128/pushres'])
check_call(['curl', '--silent', 'http://localhost:41128/lcast'])
