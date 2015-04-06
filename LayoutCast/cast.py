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
            if not absdep in list:
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

def cexec(args, failOnError = True):
    p = Popen(args, stdin=PIPE, stdout=PIPE, stderr=PIPE)
    output, err = p.communicate()
    if failOnError and p.returncode != 0:
        print('Fail to exec %s'%args)
        print(output)
        print(err)
        exit(1)
    return output

pn = package_name();
for i in range(0, 100):
    cexec(['adb', 'forward', 'tcp:41128', 'tcp:%d'%(41128+i)])
    output = cexec(['curl', 'http://127.0.0.1:41128/packagename'], failOnError = False).strip()
    if output == pn:
        print('found package '+pn+' at port %d'%(41128+i))
        break
    if i == 99:
        print('package ' + pn + ' not found');
        exit(1)

if not os.path.exists('bin/lcast/values'):
    os.makedirs('bin/lcast/values')

cexec(['curl', '--silent', '--output', 'bin/lcast/values/ids.xml', 'http://127.0.0.1:41128/ids.xml'])
cexec(['curl', '--silent', '--output', 'bin/lcast/values/public.xml', 'http://127.0.0.1:41128/public.xml'])

aaptargs = ['aapt', 'package', '-f', '--auto-add-overlay', '-F', 'bin/res.zip']
for dep in deps_list():
    aaptargs.append('-S')
    aaptargs.append(os.path.join(dep, 'res'))
aaptargs.append('-S')
aaptargs.append('res')
aaptargs.append('-S')
aaptargs.append('bin/lcast')
aaptargs.append('-M')
aaptargs.append('AndroidManifest.xml')
aaptargs.append('-I')
aaptargs.append('/Applications/android-sdk-mac_86/platforms/android-19/android.jar')
cexec(aaptargs)

print('upload and cast..')
cexec(['curl', '--silent', '-T', 'bin/res.zip', 'http://localhost:41128/pushres'])
cexec(['curl', '--silent', 'http://localhost:41128/lcast'])
