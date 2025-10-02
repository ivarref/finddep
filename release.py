#!/usr/bin/env python3
import subprocess
import traceback
import sys

LINE_PREFIX_TO_PATCH = 'clojure -Ttools install com.github.ivarref/finddep'
LINE_POSTFIX = ':as finddep'

def mod_line(line, tag, sha):
    if line.startswith("'{:git/tag "):
        return "'{:git/tag " + f'"{tag}" :git/sha ' + f'"{sha}"' + "}' \\"
    else:
        return line

RELEASE_MAJOR_MINOR_STR = "0.1"

def log_error(m):
    print(m, flush=True)

def exec_verbose(args, print_sout=False, return_sout=False):
    try:
        res = subprocess.run(args, capture_output=True)
        if res.returncode != 0:
            log_error(f'Command {args}')
            log_error(f'Failed with exit code {res.returncode}')
            if 0 == len(res.stdout.splitlines()):
                log_error('Stdout: <empty>')
            else:
                log_error('Stdout:')
                log_error('='*80)
                for lin in res.stdout.splitlines():
                    try:
                        lin_decoded = lin.decode('utf-8')
                        log_error(lin_decoded)
                    except UnicodeDecodeError:
                        log_error(lin)
            if 0 == len(res.stderr.splitlines()):
                log_error('Stderr: <empty>')
            else:
                log_error('Stderr:')
                log_error('='*80)
                for lin in res.stderr.splitlines():
                    try:
                        lin_decoded = lin.decode('utf-8')
                        log_error(lin_decoded)
                    except UnicodeDecodeError:
                        log_error(lin)
                log_error('='*80)
            log_error(f'Command {args} failed with exit code {res.returncode}')
            return False
        else:
            # info(f'CMD {str(args)} succeeded')
            sout = ''
            for lin in res.stdout.splitlines():
                try:
                    sout += lin.decode('utf-8')
                except UnicodeDecodeError:
                    sout += str(lin)
                sout += '\n'
            if print_sout:
                print(sout)
            if return_sout:
                return sout
            else:
                return True
    except FileNotFoundError as e:
        log_error(f'Executeable "{args[0]}" was not found!')
        log_error(f'Full command: {args}')
        raise e

def do_release():
    print("Running tests ...")
    if False == exec_verbose(["clojure", "-X:test"], print_sout=True):
        print("Running tests ... Failed")
        return False
    else:
        print("Running tests ... OK")
        if False == exec_verbose(["git", "update-index", "--refresh"]):
            print("There are pending changes (update-index). Aborting release!")
            return False
        else:
            if False == exec_verbose(["git", "diff-index", "--quiet", "HEAD", "--"]):
                print("There are pending changes (diff-index). Aborting release!")
                return False
            else:
                print("No changes / pending changes. Good!")
                git_sha = exec_verbose(["git", "rev-parse", "--verify", "HEAD"], return_sout=True)
                if False == git_sha:
                    print("Could not get git sha!")
                    return False
                else:
                    git_sha = git_sha.strip()
                    print(f"git sha is '{git_sha}'")
                    git_commit_count = exec_verbose(["git", "rev-list", "--count", "HEAD"], return_sout=True)
                    if False == git_commit_count:
                        print("Could not get git commit count!")
                        return False
                    else:
                        git_commit_count = git_commit_count.strip()
                        print(f"git commit count is '{git_commit_count}'")
                        global RELEASE_MAJOR_MINOR_STR
                        release_tag = f'{RELEASE_MAJOR_MINOR_STR}.{git_commit_count}'
                        print(f"Releasing tag '{release_tag}' ...")
                        cmd = ['git', 'tag', '-a', f"{release_tag}", '-m', f"Release {release_tag}"]
                        if False == exec_verbose(cmd):
                            print('Failed to git tag. Aborting!')
                            return False
                        else:
                            print("OK tag: " + " ".join(cmd))
                            cmd = ['git', 'push', '--follow-tags']
                            if False == exec_verbose(cmd):
                                print('Failed to git push. Aborting!')
                                return False
                            else:
                                print("OK push: " + " ".join(cmd))
                                print(f"Pushed tag {release_tag} with sha {git_sha}")
                                print(f"Updating README.md ...")
                                new_lines = []
                                found = False
                                with open('README.md', 'r', encoding='utf-8') as fd:
                                    for lin in fd.readlines():
                                        lin = lin.rstrip('\n')
                                        new_line = mod_line(lin, release_tag, git_sha)
                                        if new_line != lin:
                                            print(f"Patching line:\n{lin}")
                                            print(f"{new_line}")
                                            print(f"^^^ patched line")
                                            found = True
                                            new_lines.append(new_line)
                                        else:
                                            new_lines.append(lin)
                                if False == found:
                                    print('Did not find line to patch in README.md. Aborting!')
                                    return False
                                else:
                                    if '--dry' in sys.argv:
                                        print('Dry mode, exiting!')
                                        return False
                                    else:
                                        with open('README.md', 'w', encoding='utf-8') as fd:
                                            fd.write('\n'.join(new_lines))
                                            fd.write('\n')
                                        print('Updated README.md')
                                        if False == exec_verbose(['git', 'add', 'README.md']):
                                            print('Failed to git add README.md')
                                            return False
                                        else:
                                            print('git add README.md OK')
                                            cmd = ['git', 'commit', '-m', f"Update README.md for {release_tag}"]
                                            if False == exec_verbose(cmd):
                                                print('Failed to git commit. Aborting!')
                                                return False
                                            else:
                                                print("OK commit: " + " ".join(cmd))
                                                cmd = ['git', 'push']
                                                if False == exec_verbose(cmd):
                                                    print('Failed to git push. Aborting!')
                                                    return False
                                                else:
                                                    print("OK push: " + " ".join(cmd))
                                                    return True
    raise RuntimeError('Should not get here')

if __name__ == "__main__":
    print("Releasing ...")
    try:
        ret = do_release()
        if '--dry' in sys.argv:
            pass
        else:
            if ret:
                print("Releasing ... Done")
            else:
                print("Releasing ... Failed!")
    except Exception as e:
        traceback.print_exc()
        print("Releasing ... Fatal error!")