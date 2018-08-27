package vowpalWabbit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final public class NativeLibrary {
    private static final boolean DEBUG =
            System.getProperty("vowpalWabbit.NativeLibrary.DEBUG") != null;
    private static final String JNI_LIBNAME = "vw_jni";

    public static void load() {
        if (isLoaded() || tryLoadLibrary()) {
            // Either:
            // (1) The native library has already been statically loaded, OR
            // (2) The required native code has been statically linked (through a custom launcher), OR
            // (3) The native code is part of another library (such as an application-level library)
            // that has already been loaded. For example, tensorflow/examples/android and
            // tensorflow/contrib/android include the required native code in differently named libraries.
            //
            // Doesn't matter how, but it seems the native code is loaded, so nothing else to do.
            return;
        }
        // Native code is not present, perhaps it has been packaged into the .jar file containing this.
        // Extract the JNI library itself
        final String jniResourceName = makeResourceName(JNI_LIBNAME);
        log("jniResourceName: " + jniResourceName);
        final InputStream jniResource =
                NativeLibrary.class.getClassLoader().getResourceAsStream(jniResourceName);
        // Do not complain if the framework resource wasn't found. This may just mean that we're
        // building with --config=monolithic (in which case it's not needed and not included).
        if (jniResource == null) {
            throw new UnsatisfiedLinkError("Cannot find VW native library");
        }
        try {
            // Create a temporary directory for the extracted resource and its dependencies.
            final File tempPath = createTemporaryDirectory();
            // Deletions are in the reverse order of requests, so we need to request that the directory be
            // deleted first, so that it is empty when the request is fulfilled.
            tempPath.deleteOnExit();
            final String tempDirectory = tempPath.toString();
            System.load(extractResource(jniResource, JNI_LIBNAME, tempDirectory));
        } catch (IOException e) {
            throw new UnsatisfiedLinkError(
                    String.format(
                            "Unable to extract native library into a temporary file (%s)", e.toString()));
        }
    }

    private static String extractResource(
            InputStream resource, String resourceName, String extractToDirectory) throws IOException {
        final File dst = new File(extractToDirectory, System.mapLibraryName(resourceName));
        dst.deleteOnExit();
        final String dstPath = dst.toString();
        log("extracting native library to: " + dstPath);
        final long nbytes = copy(resource, dst);
        log(String.format("copied %d bytes to %s", nbytes, dstPath));
        return dstPath;
    }

    private static boolean tryLoadLibrary() {
        try {
            System.loadLibrary(JNI_LIBNAME);
            return true;
        } catch (UnsatisfiedLinkError e) {
            log("tryLoadLibraryFailed: " + e.getMessage());
            return false;
        }
    }

    private static boolean isLoaded() {
        try {
            VW.version();
            log("isLoaded: true");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static void log(String msg) {
        if (DEBUG) {
            System.err.println("org.tensorflow.NativeLibrary: " + msg);
        }
    }

    private static String makeResourceName(String baseName) {
        return System.mapLibraryName(baseName);
    }

    private static long copy(InputStream src, File dstFile) throws IOException {
        FileOutputStream dst = new FileOutputStream(dstFile);
        try {
            byte[] buffer = new byte[1 << 20]; // 1MB
            long ret = 0;
            int n = 0;
            while ((n = src.read(buffer)) >= 0) {
                dst.write(buffer, 0, n);
                ret += n;
            }
            return ret;
        } finally {
            dst.close();
            src.close();
        }
    }

    // Shamelessly adapted from Guava to avoid using java.nio, for Android API
    // compatibility.
    private static File createTemporaryDirectory() {
        File baseDirectory = new File(System.getProperty("java.io.tmpdir"));
        String directoryName = "tensorflow_native_libraries-" + System.currentTimeMillis() + "-";
        for (int attempt = 0; attempt < 1000; attempt++) {
            File temporaryDirectory = new File(baseDirectory, directoryName + attempt);
            if (temporaryDirectory.mkdir()) {
                return temporaryDirectory;
            }
        }
        throw new IllegalStateException(
                "Could not create a temporary directory (tried to make "
                        + directoryName
                        + "*) to extract TensorFlow native libraries.");
    }

    private NativeLibrary() {}
}
