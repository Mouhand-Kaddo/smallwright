#!/usr/bin/env python3
"""Opt-in whole framework-opengl-sources experiment; never a worker input.

The exact upstream 52-file module is isolated. Public source-built resolution
substitutes for canonical Soong dependencies; no SDK definition feeds tools.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import unittest
import uuid
import zipfile

from compare_platform_abi import ClassFile, classes, compare
from test_network_module_source_headers import digest, tool_identity

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "toolchains/platform-api36-source-probe"
RAW_ROOT = "base-r2-sparse/opengl/java"
BASELINE = "libcore-builder-20261002/aggregate-2265914c3d69485487387a9e8eecd83f/headers.jar"
POLICY = "framework-flags-r2-public-modules/exportable-flags-config.xml"
HISTORY = "api35-release-20261002/ap3a-filtered/filtered-api.txt"
LINT_BASELINE = "base-r2-sparse/core/api/lint-baseline.txt"
RAW_PINS = {
    "base-r2-sparse/opengl/java/android/opengl/EGL14.java": "508ee638d6e57ada3db0f37ef860353cacd70d8e6793320c63d9a356a3bc97d8",
    "base-r2-sparse/opengl/java/android/opengl/EGL15.java": "95844c69661d6d0b34b392058cfe46e9e9c1177b24e0131d4e12bfafa1127e12",
    "base-r2-sparse/opengl/java/android/opengl/EGLConfig.java": "9860741f20eda4a63062a2752d0e591d8017a3117f6396d212cc18e3fd490052",
    "base-r2-sparse/opengl/java/android/opengl/EGLContext.java": "091c00830f0afad48d0b25d4499d40b2d0e9a3851e37b75f662f6bd269d4463d",
    "base-r2-sparse/opengl/java/android/opengl/EGLDisplay.java": "789b574d672f2102a79dd3af995c6b5b39c5c54ed11b9b619abcfc85dba9cad2",
    "base-r2-sparse/opengl/java/android/opengl/EGLExt.java": "135d01795d6b31feebf1af1c79fce34c3ff18ea7f246181540b9226ed391e40a",
    "base-r2-sparse/opengl/java/android/opengl/EGLImage.java": "ba9850a778240cb4b06fea9faee005fb4c01c0c2d7d22ac172f78ef65af62925",
    "base-r2-sparse/opengl/java/android/opengl/EGLLogWrapper.java": "7f4c5086d66ebc9496c23eff4a3e9426e0ea923b34d1d54fe88327e37de6e799",
    "base-r2-sparse/opengl/java/android/opengl/EGLObjectHandle.java": "e5a104f1f6542d44ddb5a9fe4fc1f8ff3b076f112930a708e706f36e42764a24",
    "base-r2-sparse/opengl/java/android/opengl/EGLSurface.java": "a2b77c3b8fabb673248def0f7d0a4a13ff8852a751c3f1a5bd4a7aa9540da206",
    "base-r2-sparse/opengl/java/android/opengl/EGLSync.java": "27de7a755f91a3373b7048ea30621ce58d444ea3497cf0e5374760a30ef29034",
    "base-r2-sparse/opengl/java/android/opengl/ETC1.java": "abc634a6c4b8fd72f78a3732616f040dc61e57ca31a60e354778f5b861015a94",
    "base-r2-sparse/opengl/java/android/opengl/ETC1Util.java": "fd2598134510f7b6f6a211dcb8563d43ec221f15024345aadd4e9d51e7664535",
    "base-r2-sparse/opengl/java/android/opengl/GLDebugHelper.java": "cce2969cb9242cf6b06d5a0264b7ae0b6ff87058794e44ba3e9462d7e8c42aef",
    "base-r2-sparse/opengl/java/android/opengl/GLES10.java": "1ace0410e89213ce4a2f82f15115f1bd5dbec6f8d9fa56b50f21fb511d567e7b",
    "base-r2-sparse/opengl/java/android/opengl/GLES10Ext.java": "923dc27fae2622c75b4694e107a2c461a55fca62d464b9826a6547e801c4e310",
    "base-r2-sparse/opengl/java/android/opengl/GLES11.java": "dbffb10cb98afe88f855deb54a3ae1578bc619a67628506b664b845a35d96667",
    "base-r2-sparse/opengl/java/android/opengl/GLES11Ext.java": "e966957f28d6e6646e7e9caf5efcf780b0d9adbe8af7a14b40de1f1c36ada5bb",
    "base-r2-sparse/opengl/java/android/opengl/GLES20.java": "aeb1c32996b6840d9b06723cd2af44f69e76cd98eea4a8afde7f1de3619c1365",
    "base-r2-sparse/opengl/java/android/opengl/GLES30.java": "06f97ccf18659422bda14da7d707a63240f8a22090dcf1ab560a927d69ae92ca",
    "base-r2-sparse/opengl/java/android/opengl/GLES31.java": "ec81fa0f5981046d3263e0e584f752986daede4add792e6a299c48cdbc0de3f6",
    "base-r2-sparse/opengl/java/android/opengl/GLES31Ext.java": "47fd6c9852cf2238e346aa7def552b92758e459f10710d6a8a9e47d9889291b7",
    "base-r2-sparse/opengl/java/android/opengl/GLES32.java": "18c33d8862775f17eb1c4c97ba88dd7440650a102198fd31f14dcc33368f308c",
    "base-r2-sparse/opengl/java/android/opengl/GLErrorWrapper.java": "bdd8ce1bfcf295ff5d0c5aba355cf8adedf2ef29e76c8f777f6b619d2abd01bb",
    "base-r2-sparse/opengl/java/android/opengl/GLException.java": "9a4fa797bb0e2fefc735e16ee63a4a3dabce31f542dcd46e144616392173f7c9",
    "base-r2-sparse/opengl/java/android/opengl/GLLogWrapper.java": "b48bbf4a4383129eabb731c4c5f40137feca4363ac769fc6433b574adc862916",
    "base-r2-sparse/opengl/java/android/opengl/GLSurfaceView.java": "49581ab937901f9b476ac6ec9c414f63bcb98fa25ac6904eb38dcc5b0fa37b43",
    "base-r2-sparse/opengl/java/android/opengl/GLU.java": "f502da84ff34165c6b628bcad39ea991597b40f87619f780312d6c01e0aa2d30",
    "base-r2-sparse/opengl/java/android/opengl/GLUtils.java": "4e248e12af739c467cf3f7a7e5c8652519fca33b9ac98011273d5af1f0af0246",
    "base-r2-sparse/opengl/java/android/opengl/GLWallpaperService.java": "970a0806ec06a44d7e97a829de0a2e517837bbb896ce824939016fafe71f350c",
    "base-r2-sparse/opengl/java/android/opengl/GLWrapperBase.java": "af7359af900ef695d27f6f1ec16c435541104663a18f273b1f06ab5ff28ecdd7",
    "base-r2-sparse/opengl/java/android/opengl/Matrix.java": "0862f3ac5b69df36b6632d9cb61bb2ea4288ca38bf5d976ec4a94a00e2e7e91d",
    "base-r2-sparse/opengl/java/android/opengl/Visibility.java": "a0388bf83888526b0b2f0feab3e97e604f5d8f8fb26f0ea398712b156b6e29f2",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/EGLConfigImpl.java": "b024385a5e6efb50a24515099ce2acfed7ef72533fbcdb6b89f284ea82d30e2f",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/EGLContextImpl.java": "86ba2121511997743bb3718b63fa8f2eeed438cbdcae6850e97ee52a5ea2b705",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/EGLDisplayImpl.java": "9327f39ee7fb847523282220469c7f6786dff5afe4fa07a5d7f9afc1d9109166",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/EGLImpl.java": "549cab3a2f0bc503e4cee46f9981f2f443ef26878290cac8c98e1bbb4e4f5288",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/EGLSurfaceImpl.java": "6544c86105d9337befa177f8e6ddfbbb7a7ed6eb5988adf5db85e35601fc9a3a",
    "base-r2-sparse/opengl/java/com/google/android/gles_jni/GLImpl.java": "c7328d3c1741f508c9124ed674702843e0ebb1817370c4d2d040c44c64503356",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGL.java": "10f40a2630c178ec3cf666dd024aa4b443971cac29ac1dd641968a690c1dd7ce",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGL10.java": "f5f7b277e13e0f439c638285668701cf97ed6fcb31e2261c62ac03fecad2382d",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGL11.java": "e5f9ddc2b603a48dba9858443fad0c4e24777d21559943202f207eb22e6b2a78",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGLConfig.java": "f029fd976b8c3dc96d8338814b0f831cceeace22d2cbc09cb2f5ebb2cc39985b",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGLContext.java": "1944df3df4fb257375de7d2f25f677c05d5dbab1d13281d1f56da45f4f9d2328",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGLDisplay.java": "b04982614fe9df3a8071e4328ceb54371062ab2b014d9bbb24f8b5719e64001a",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/egl/EGLSurface.java": "fb4cbb518956c0acd8c32279aa29d992419a86079143f036313eea6772b74675",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL.java": "400bd0f6c9f441dc27a49165b028ca9aba4575e0f7b30f7d9bd1d2e28430781e",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL10.java": "676f01e69386b138b2b3d397c11952c7cc73271cd42a29a9ed29304fa7889bb6",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL10Ext.java": "eb103edcab50fdd6fb7fa1027d583547d9a9acb112258d0d0efef92be09216cf",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL11.java": "d89e78cdcfdbfaed2563153576274e25d75437cfe71564a7b7de987a3b325577",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL11Ext.java": "045b01e9adb0c86b916ed5c97097bd0c2e72c662f1f7d92d537d25ea9f96f5d0",
    "base-r2-sparse/opengl/java/javax/microedition/khronos/opengles/GL11ExtensionPack.java": "f3772af4e3fedd618739d038bde5e94f68898862e8fe8b20f618c691024a6ed9"
}
INPUT_PINS = {
    "base-r2-sparse/opengl/java/Android.bp": "222ded3f19f2b8aea67f3f8e8bd21d39bf3b30fe22b93dd61ec8e0053d1799e8",
    "base-r2-sparse/Android.bp": "66e418a71da9eeb993c71938cb89297e401fb5ebdb1c692b4e294b2590d04e79",
    "base-r2-sparse/api/Android.bp": "3ad8450e396ad883047a49da5298a163c2c2781aa7a635b548aad6dc3eac2231",
    "libcore-builder-20261002/aggregate-2265914c3d69485487387a9e8eecd83f/headers.jar": "e36f41ffc131afab50024cdc1d269ba1bd96c06387d3662580c7d7af868fe27a",
    "framework-flags-r2-public-modules/exportable-flags-config.xml": "671edeeefdea02de7552383e34dfd64059f556ec738be5eb319d4ab7b162d2e3",
    "api35-release-20261002/ap3a-filtered/filtered-api.txt": "22f960867194fbdfcbc03ca09cec36468f847524966af5f572e43f1d315950ab",
    "opengl-module-20261002/helper-fetch-58fde64fe0c745faa850d52e56ee0fc9/java/android/compat/annotation/Android.bp": "db0664e3ac5ce4a33ab69b73956c360b1edf345806e13326f699b0266a7027dd",
    "opengl-module-20261002/helper-fetch-58fde64fe0c745faa850d52e56ee0fc9/receipt.json": "7633b8dc49516f02729650c89f3c5dc5b08c58289f50f408900816ea707e3a96",
    "metalava/stub-annotations/src/main/java/android/annotation/NonNull.java": "abfade84be1f981581becbedee722e036c4469f3593f9924311df7803ab5d3ff",
    "metalava/stub-annotations/src/main/java/androidx/annotation/NonNull.java": "8fd3ed930e2c4124cd0c4b0fbfa950f79fa7c68c61b0a48c98401d364d83ac27",
    "opengl-module-20261002/helper-fetch-58fde64fe0c745faa850d52e56ee0fc9/java/android/compat/annotation/UnsupportedAppUsage.java": "80e83654d128662e4f93f2ad003d033fd4ff9bd13ef3bb805a28f1f898f8ecda",
    "base-r2-sparse/core/api/lint-baseline.txt": "2b41bf5cd9a6eb8c9904721995e070b69f3922131019697941417eae642ccad6",
    "base-r2-sparse/core/api/Android.bp": "6523e73a33641e44de584ee1c51f52df8ce5f4bcd9ef802f98689f64b4858223",
    "base-r2-sparse/api/StubLibraries.bp": "4614cba166de629f6fa613e653d0a61b36e07723583f23d2f5d8964b42f0e82b"
}
HELPERS = [
    "metalava/stub-annotations/src/main/java/android/annotation/NonNull.java",
    "metalava/stub-annotations/src/main/java/androidx/annotation/NonNull.java",
    "opengl-module-20261002/helper-fetch-58fde64fe0c745faa850d52e56ee0fc9/java/android/compat/annotation/UnsupportedAppUsage.java"
]
PREFIXES = ("android/opengl/", "javax/microedition/khronos/egl/", "javax/microedition/khronos/opengles/")
WARNING_POLICY = "+*:-android.*:+android.icu.*:-dalvik.*"
# Independently read actual declarations: EGL*.equals at line30,
# EGLObjectHandle.hashCode at61; GLSurfaceView.finalize at227 and lifecycle at610/630.
OVERRIDES = {
    **{"android/opengl/" + n + ".class": {("equals", "(Ljava/lang/Object;)Z"): (1, {})}
       for n in ("EGLConfig", "EGLContext", "EGLDisplay", "EGLImage", "EGLSurface", "EGLSync")},
    "android/opengl/EGLObjectHandle.class": {("hashCode", "()I"): (1, {})},
    "android/opengl/GLSurfaceView.class": {
        ("finalize", "()V"): (4, {"Exceptions": ("java/lang/Throwable",)}),
        ("onAttachedToWindow", "()V"): (4, {}),
        ("onDetachedFromWindow", "()V"): (4, {})},
}


def pin_inputs():
    for name, expected in {**RAW_PINS, **INPUT_PINS}.items():
        if digest(PROBE / name) != expected:
            raise AssertionError("Source/input pin mismatch: " + name)
    bp = (PROBE / (RAW_ROOT + "/Android.bp")).read_text()
    if not re.search(r'name:\s*"framework-opengl-sources",\s*srcs:\s*\["\*\*/\*\.java"\]', bp):
        raise AssertionError("Unsupported upstream source selection")
    actual = {p.relative_to(PROBE).as_posix() for p in (PROBE / RAW_ROOT).rglob("*.java")}
    if actual != set(RAW_PINS) or len(actual) != 52:
        raise AssertionError("Upstream 52-file glob inventory changed")
    # This is independently the framework's own policy, not borrowed from libcore.
    framework_bp = (PROBE / "base-r2-sparse/api/Android.bp").read_text()
    defaults = framework_bp.split('name: "android-non-updatable-stubs-defaults",', 1)[1]
    defaults = defaults.split('soong_config_module_type {', 1)[0]
    if '"--force-convert-to-warning-nullability-annotations ' + WARNING_POLICY + '"' not in defaults:
        raise AssertionError("Pinned framework nullability policy is missing")


def native_declarations():
    """Read native descriptors independently from raw binding declarations.

    These bindings have nongeneric Java types. Reject unsupported syntax rather
    than approximating or learning expected modifiers from emitted stubs/SDK.
    """
    primitive = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
                 "int": "I", "long": "J", "float": "F", "double": "D"}
    result = {}
    for name in RAW_PINS:
        text = (PROBE / name).read_text()
        text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
        package = re.search(r'package\s+([\w.]+)\s*;', text).group(1)
        imports = {x.rsplit('.', 1)[1]: x for x in re.findall(r'import\s+([\w.]+)\s*;', text)}
        owner = package.replace('.', '/') + '/' + Path(name).stem + '.class'
        nested_types = set(re.findall(r'\b(?:class|interface|enum)\s+(\w+)', text)) - {Path(name).stem}
        def descriptor(t):
            t = re.sub(r'@[\w.]+(?:\([^)]*\))?\s*', '', t).strip().replace(' ', '')
            arrays = ''
            while t.endswith('[]'):
                arrays += '['; t = t[:-2]
            if t in primitive: return arrays + primitive[t]
            if not re.fullmatch(r'[\w.]+', t): raise AssertionError("Unsupported native type: " + t)
            qualified = imports.get(t, t if '.' in t else
                                    'java.lang.' + t if t in ('String', 'Object') else package + '.' + t)
            if t in nested_types:
                qualified = package + '.' + Path(name).stem + '$' + t
            return arrays + 'L' + qualified.replace('.', '/') + ';'
        declarations = {}
        pattern = r'public\s+(static\s+)?native\s+([\w.\[\]]+)\s+(\w+)\s*\((.*?)\)\s*;'
        for static, returns, method, parameters in re.findall(pattern, text, flags=re.S):
            params = []
            for parameter in parameters.split(',') if parameters.strip() else []:
                parameter = re.sub(r'@[\w.]+(?:\([^)]*\))?\s*', '', parameter).strip()
                match = re.fullmatch(r'(?:final\s+)?(.+?)\s+(\w+)', parameter)
                if not match: raise AssertionError("Unsupported native parameter: " + parameter)
                params.append(descriptor(match.group(1)))
            key = (method, '(' + ''.join(params) + ')' + descriptor(returns))
            if key in declarations: raise AssertionError("Duplicate native declaration")
            declarations[key] = 0x101 | (8 if static else 0)
        result[owner] = declarations
    return result


@unittest.skipUnless(os.environ.get("PLB_OPENGL_MODULE_INTEGRATION") == "1",
                     "requires pinned whole raw module, helper sources, source headers and host tools")
class OpenGlModuleSourceIntegrationTest(unittest.TestCase):
    def test_released_whole_raw_module_preserves_exact_metadata(self):
        pin_inputs()
        baseline = classes(PROBE / BASELINE)
        expected = {n: ClassFile(v, n) for n, v in baseline.items() if n.startswith(PREFIXES)}
        natives = native_declarations()
        jdk = ROOT / 'toolchains/jdk-21.0.12.1+1'
        metalava = PROBE / 'out-r4/metalava/metalava/build/install/metalava/bin/metalava'
        output = PROBE / 'opengl-module-20261002' / ('integration-' + uuid.uuid4().hex)
        output.mkdir(parents=True)
        environment = {'JAVA_HOME': str(jdk), 'JAVA_OPTS': '-Xms128m -Xmx1024m',
                       'PATH': str(jdk / 'bin') + ':/usr/bin:/bin', 'LANG': 'C.UTF-8'}
        receipt = {'status': 'experimental_public_source_classpath_not_canonical_module_closure',
                   'inputs': {**RAW_PINS, **INPUT_PINS}, 'tools': tool_identity(jdk, metalava),
                   'module': 'framework-opengl-sources', 'glob': '**/*.java', 'source_count': 52,
                   'child_environment': environment, 'stages': [], 'source_copies': [],
                   'source_commits': {'frameworks/base': '99b01a65cc4c104933788b3143285ab6bae65827',
                                      'metalava': '9809e0d70a5b807be3d7070d51f9c4714f2fdcb4',
                                      'tools/platform-compat': 'db0de6104d8527114a7ae441b0a9c35f885d2876'}}
        receipt['test_script'] = {'path': str(Path(__file__).resolve()),
                                  'sha256': digest(Path(__file__))}
        receipt['strict_comparator'] = {'path': str(ROOT / 'scripts/compare_platform_abi.py'),
                                        'sha256': digest(ROOT / 'scripts/compare_platform_abi.py')}
        def save():
            (output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
        def profile(models):
            return {n: {'flags': c.flags, 'superclass': c.superclass, 'interfaces': c.interfaces,
                        'attributes': c.attributes,
                        'fields': [(k, v) for k, v in sorted(c.fields.items())],
                        'methods': [(k, v) for k, v in sorted(c.methods.items())]}
                    for n, c in sorted(models.items())}
        (output / 'baseline-profile.json').write_text(json.dumps(profile(expected), indent=2) + '\n')
        target = ('glActiveTexture', '(I)V')
        with self.assertRaises(AssertionError) as red:
            self.assertEqual(0x109, expected['android/opengl/GLES20.class'].methods[target][0])
        receipt['red'] = {'method': target, 'expected_flags': '0x109', 'actual_flags': '0x9',
                          'assertion_failure': str(red.exception),
                          'missing_raw_overrides': [(n, sorted(m)) for n, m in OVERRIDES.items()]}
        (output / 'red.log').write_text(str(red.exception) + '\n')
        for n, additions in OVERRIDES.items():
            self.assertFalse(set(additions) & expected[n].methods.keys())
        sources = []
        source_root = output / 'module-source'
        for name in RAW_PINS:
            target_path = source_root / Path(name).relative_to(RAW_ROOT)
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_bytes((PROBE / name).read_bytes())
            self.assertEqual(RAW_PINS[name], digest(target_path))
            sources.append(target_path)
            receipt['source_copies'].append({'original': name, 'path': str(target_path),
                                            'sha256': digest(target_path)})
        self.assertEqual(sorted(sources), sorted(source_root.rglob('*.java')))
        helper_sources = []
        for name in HELPERS:
            target_path = output / 'helper-source' / name.split('/java/', 1)[1]
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_bytes((PROBE / name).read_bytes())
            self.assertEqual(INPUT_PINS[name], digest(target_path))
            helper_sources.append(target_path)
            receipt['source_copies'].append({'original': name, 'path': str(target_path),
                                            'sha256': digest(target_path)})
        save()  # Source, helper and actual installed tool identity precede the first launch.
        print('OPENGL_EXPERIMENT=' + str(output), flush=True)
        def run(name, command):
            command = [str(v) for v in command]
            started = time.monotonic()
            result = subprocess.run(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=90)
            (output / (name + '.log')).write_bytes(result.stdout)
            receipt['stages'].append({'name': name, 'command': command, 'exit_code': result.returncode,
                                      'seconds': time.monotonic() - started})
            diagnostics = result.stdout.decode(errors='replace')
            unresolved = [line for line in diagnostics.splitlines() if '[UnresolvedImport]' in line]
            if unresolved:
                receipt['module_closure_unresolved_imports'] = unresolved
            if result.returncode:
                receipt['result'] = 'blocked actual tool stage; no candidate or GREEN claim'
                receipt['failure'] = {'stage': name, 'exit_code': result.returncode,
                                      'unresolved_imports': unresolved,
                                      'emitted_source_inventory': [p.relative_to(output / 'stubs').as_posix()
                                          for p in sorted((output / 'stubs').rglob('*.java'))]}
            save()
            self.assertEqual(0, result.returncode, name + ': ' + diagnostics[-4000:])
        def compile_java(name, cp, java):
            destination = output / name; destination.mkdir()
            run(name, [jdk / 'bin/javac', '-source', '8', '-target', '8', '-proc:none',
                       '-bootclasspath', cp, '-classpath', '', '-sourcepath', '', '-d', destination, *java])
            return destination
        helpers = compile_java('helper-classes', str(PROBE / BASELINE), helper_sources)
        cp = str(helpers) + ':' + str(PROBE / BASELINE)
        stubs = output / 'stubs'
        run('raw-module-metalava', [metalava, '--quiet', 'main', '--include-annotations',
            '--exclude-documentation-from-stubs', '--stub-packages',
            'android.opengl:javax.microedition.khronos.egl:javax.microedition.khronos.opengles',
            '--source-path', source_root, '--classpath', cp, '--stubs', stubs,
            '--api', output / 'released-api.txt', '--config-file', PROBE / POLICY,
            '--check-compatibility', 'disabled', '--check-compatibility:api:released', PROBE / HISTORY,
            '--baseline:api-lint', PROBE / LINT_BASELINE,
            '--force-convert-to-warning-nullability-annotations', WARNING_POLICY,
            *sources])
        emitted = sorted(stubs.rglob('*.java'))
        receipt['emitted_sources'] = [p.relative_to(stubs).as_posix() for p in emitted]
        save()
        # Preserve every error/warning; no hidden implementation or unresolved helper is suppressed.
        compiler = compile_java('module-classes', cp, emitted)
        actual = {p.relative_to(compiler).as_posix(): ClassFile(p.read_bytes(), p.relative_to(compiler).as_posix())
                  for p in compiler.rglob('*.class')}
        (output / 'actual-profile.json').write_text(json.dumps(profile(actual), indent=2) + '\n')
        self.assertEqual(set(expected), set(actual), 'Whole emitted class inventory')
        changes = []
        for n, before in expected.items():
            after = actual[n]
            self.assertEqual(before.flags, after.flags, n)
            self.assertEqual(before.superclass, after.superclass, n)
            self.assertEqual(before.interfaces, after.interfaces, n)
            self.assertEqual(before.attributes, after.attributes, n)
            self.assertEqual(before.fields, after.fields, n)
            self.assertEqual(before.methods.keys() | OVERRIDES.get(n, {}).keys(), after.methods.keys(), n)
            for key, (flags, attrs) in before.methods.items():
                desired_flags = natives.get(n, {}).get(key, flags)
                self.assertEqual((desired_flags, attrs), after.methods[key], n + ':' + repr(key))
                if desired_flags != flags: changes.append((n, key, hex(flags), hex(desired_flags)))
            for key, metadata in OVERRIDES.get(n, {}).items():
                self.assertEqual(metadata, after.methods[key], n + ':' + repr(key))
        receipt['public_emitted_abi'] = {'status': 'GREEN', 'classes': len(actual),
                                        'native_changes': changes,
                                        'raw_overrides': [(n, sorted(m)) for n, m in OVERRIDES.items()]}
        candidate = output / 'raw-module-probe.jar'
        with zipfile.ZipFile(PROBE / BASELINE) as original, zipfile.ZipFile(candidate, 'x') as archive:
            for info in original.infolist():
                archive.writestr(info, (compiler / info.filename).read_bytes() if info.filename in actual
                                 else original.read(info.filename))
        revised = classes(candidate)
        self.assertEqual(baseline.keys(), revised.keys())
        self.assertTrue(all(baseline[n] == revised[n] for n in baseline.keys() - actual.keys()))
        receipt['output'] = {'path': str(candidate), 'sha256': digest(candidate)}
        save()
        # SDK opens only after source-only extraction/compilation/inventory GREEN.
        sdk = ROOT / 'toolchains/android-sdk/platforms/android-36/android.jar'
        sdk_classes = classes(sdk)
        self.assertEqual([], [n for n in actual if ClassFile(sdk_classes[n], n).abi() != actual[n].abi()])
        strict = compare(sdk, candidate)
        (output / 'strict-abi.json').write_text(json.dumps(strict, indent=2) + '\n')
        receipt['sdk_diagnostic_only'] = {'path': str(sdk), 'sha256': digest(sdk),
                                         'different_classes': len(strict['different_classes']),
                                         'public_api_match': strict['public_api_match']}
        receipt['module_closure'] = {'status': 'RED' if receipt.get('module_closure_unresolved_imports')
                                              else 'experimental_public_classpath_not_canonical',
                                    'unresolved_imports': receipt.get('module_closure_unresolved_imports', [])}
        receipt['result'] = ('public emitted source ABI GREEN; whole-module closure RED' if
                            receipt.get('module_closure_unresolved_imports') else
                            'source-derived public emitted ABI GREEN; experimental substituted classpath, not release')
        save()
        self.assertFalse(receipt.get('module_closure_unresolved_imports'),
                         'Public emitted ABI does not establish hidden-source module closure')


if __name__ == '__main__':
    unittest.main()
