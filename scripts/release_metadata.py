"""Prepare public updater metadata from the actual signed APK's aapt output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

REPO = 'Amine-AO/JustWatch'


def version_tuple(name):
    if not re.fullmatch(r'\d+\.\d+\.\d+', name):
        raise ValueError('Use a stable versionName such as 1.0.1, without -dev')
    return tuple(map(int, name.split('.')))


def check_increase(code, name, previous_code=None, previous_name=None):
    current = version_tuple(name)
    if code <= 0:
        raise ValueError('versionCode must be positive')
    if previous_code is not None and code <= previous_code:
        raise ValueError('Increase versionCode in app/build.gradle.kts before releasing an update')
    if previous_name is not None and current <= version_tuple(previous_name):
        raise ValueError('Increase versionName in app/build.gradle.kts before releasing an update')


def get_json(url):
    headers = {'User-Agent': 'JustWatch-release-builder', 'Accept': 'application/vnd.github+json'}
    token = os.environ.get('GH_TOKEN')
    if token and urllib.parse.urlparse(url).hostname == 'api.github.com':
        headers['Authorization'] = f'Bearer {token}'
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
        data = response.read(1024 * 1024 + 1)
    if len(data) > 1024 * 1024:
        raise ValueError('Release metadata is too large')
    return json.loads(data)


def previous_release():
    try:
        release = get_json(f'https://api.github.com/repos/{REPO}/releases/latest')
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None, None
        raise
    name = release['tag_name'].removeprefix('v')
    code = None
    for asset in release.get('assets', []):
        if asset['name'] == 'update.json':
            url = asset['browser_download_url']
            parsed = urllib.parse.urlparse(url)
            if parsed.scheme != 'https' or parsed.netloc != 'github.com' or not parsed.path.startswith(f'/{REPO}/releases/download/'):
                raise ValueError('Unexpected previous metadata address')
            previous = get_json(url)
            code, name = int(previous['versionCode']), previous['versionName']
            break
    return code, name


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--badging', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    line = next(line for line in args.badging.read_text().splitlines() if line.startswith('package:'))
    fields = dict(re.findall(r"(\w+)='([^']*)'", line))
    if fields['name'] != 'com.justwatch.links':
        raise ValueError('Unexpected APK package')
    code, name = int(fields['versionCode']), fields['versionName']
    check_increase(code, name, *previous_release())
    apk = args.apk.read_bytes()
    data = dict(packageName=fields['name'], versionCode=code, versionName=name,
                apkUrl=f'https://github.com/{REPO}/releases/download/v{name}/JustWatch-release.apk',
                sha256=hashlib.sha256(apk).hexdigest(), size=len(apk))
    args.output.write_text(json.dumps(data, indent=2) + '\n')
    output = os.environ.get('GITHUB_OUTPUT')
    if output:
        with open(output, 'a') as stream:
            stream.write(f'version_name={name}\nversion_code={code}\n')
    print(f'Prepared updater metadata for JustWatch {name} (code {code})')


if __name__ == '__main__':
    main()
