package dev.smallwright.worker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The sources of AOSP libcore's {@code core-lambda-stubs-for-system-modules} (filegroup {@code
 * openjdk_lambda_stub_files} at libcore commit de876a01b29230b877c9f408348c38a90ee724a5), shipped
 * unchanged as worker assets under the GPLv2 with the Classpath exception. They declare the three
 * {@code java.lang.invoke} classes that ECJ needs for lambdas, method references and serializable
 * lambdas and that Google's android.jar leaves out. They are compile-only: D8 desugars every use,
 * and only the generated app's package is dexed.
 */
final class LambdaStubs {
    static final String ASSET_DIRECTORY = "core-lambda-stubs";
    static final Map<String, String> SHA256 =
            new TreeMap<>(
                    Map.of(
                            "java/lang/invoke/LambdaMetafactory.java",
                            "d94fc80083704fbc9ed1d004a677ec596a91d472559d20ef2556a2a6927d40fe",
                            "java/lang/invoke/SerializedLambda.java",
                            "b961edb25ed34896d4dfe6ee9566504936db3802fb875e512dd4086e86219afd",
                            "java/lang/invoke/StringConcatFactory.java",
                            "0df4096a88c55e12cdbb938bab218663e16628c984ac7a42e8fe519b9295f6aa"));

    interface Assets {
        InputStream open(String path) throws IOException;
    }

    private LambdaStubs() {}

    /** Copies each pinned source into {@code scratch}, refusing any whose SHA-256 differs. */
    static List<File> write(Assets assets, File scratch) throws IOException {
        List<File> sources = new ArrayList<>();
        for (Map.Entry<String, String> stub : SHA256.entrySet()) {
            String path = ASSET_DIRECTORY + "/" + stub.getKey();
            File source = new File(scratch, path);
            Files.createDirectories(source.getParentFile().toPath());
            try (InputStream input = assets.open(path)) {
                Files.copy(input, source.toPath());
            }
            if (!LibraryInputs.sha256(source).equals(stub.getValue())) {
                Files.delete(source.toPath());
                throw new IOException(
                        "Lambda stub source does not match its pin: " + stub.getKey());
            }
            sources.add(source);
        }
        return sources;
    }
}
