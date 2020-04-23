package com.cgb.bcpinstall.common.util;

import com.google.common.io.ByteStreams;
import io.netty.util.internal.StringUtil;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

public final class Utils {
    private static final Log logger = LogFactory.getLog(Utils.class);

    private static final boolean TRACE_ENABED = logger.isTraceEnabled();

    /**
     * Compress the contents of given directory using Tar and Gzip to an in-memory byte array.
     *
     * @param sourceDirectory  the source directory.
     * @param pathPrefix       a path to be prepended to every file name in the .tar.gz output, or {@code null} if no prefix is required.
     * @param chaincodeMetaInf
     * @return the compressed directory contents.
     * @throws IOException
     */
    public static byte[] generateTarGz(File sourceDirectory, String pathPrefix, File chaincodeMetaInf) throws IOException {
        logger.trace(format("generateTarGz: sourceDirectory: %s, pathPrefix: %s, chaincodeMetaInf: %s",
                sourceDirectory == null ? "null" : sourceDirectory.getAbsolutePath(), pathPrefix,
                chaincodeMetaInf == null ? "null" : chaincodeMetaInf.getAbsolutePath()));

        ByteArrayOutputStream bos = new ByteArrayOutputStream(4 * 1024);

        String sourcePath = sourceDirectory.getAbsolutePath();

        TarArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(bos));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

        try {
            Collection<File> childrenFiles = org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true);

            ArchiveEntry archiveEntry;
            FileInputStream fileInputStream;
            for (File childFile : childrenFiles) {
                String childPath = childFile.getAbsolutePath();
                String relativePath = childPath.substring((sourcePath.length() + 1), childPath.length());

                if (pathPrefix != null) {
                    relativePath = Utils.combinePaths(pathPrefix, relativePath);
                }

                relativePath = FilenameUtils.separatorsToUnix(relativePath);

                if (TRACE_ENABED) {
                    logger.trace(format("generateTarGz: Adding '%s' entry from source '%s' to archive.", relativePath, childFile.getAbsolutePath()));
                }

                archiveEntry = new TarArchiveEntry(childFile, relativePath);
                fileInputStream = new FileInputStream(childFile);
                archiveOutputStream.putArchiveEntry(archiveEntry);

                try {
                    IOUtils.copy(fileInputStream, archiveOutputStream);
                } finally {
                    IOUtils.closeQuietly(fileInputStream);
                    archiveOutputStream.closeArchiveEntry();
                }

            }

            if (null != chaincodeMetaInf) {
                childrenFiles = org.apache.commons.io.FileUtils.listFiles(chaincodeMetaInf, null, true);

                final URI metabase = chaincodeMetaInf.toURI();

                for (File childFile : childrenFiles) {

                    final String relativePath = Paths.get("META-INF", metabase.relativize(childFile.toURI()).getPath()).toString();

                    if (TRACE_ENABED) {
                        logger.trace(format("generateTarGz: Adding '%s' entry from source '%s' to archive.", relativePath, childFile.getAbsolutePath()));
                    }

                    archiveEntry = new TarArchiveEntry(childFile, relativePath);
                    fileInputStream = new FileInputStream(childFile);
                    archiveOutputStream.putArchiveEntry(archiveEntry);

                    try {
                        IOUtils.copy(fileInputStream, archiveOutputStream);
                    } finally {
                        IOUtils.closeQuietly(fileInputStream);
                        archiveOutputStream.closeArchiveEntry();
                    }

                }

            }
        } finally {
            IOUtils.closeQuietly(archiveOutputStream);
        }

        return bos.toByteArray();
    }

    /**
     * Read the contents a file.
     *
     * @param input source file to read.
     * @return contents of the file.
     * @throws IOException
     */
    public static byte[] readFile(File input) throws IOException {
        return Files.readAllBytes(Paths.get(input.getAbsolutePath()));
    }

    /**
     * Generate a v4 UUID
     *
     * @return String representation of {@link UUID}
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Delete a file or directory
     *
     * @param file {@link File} representing file or directory
     * @throws IOException
     */
    public static void deleteFileOrDirectory(File file) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                Path rootPath = Paths.get(file.getAbsolutePath());

                Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } else {
                file.delete();
            }
        } else {
            throw new RuntimeException("File or directory does not exist");
        }
    }

    /**
     * Combine two or more paths
     *
     * @param first parent directory path
     * @param other children
     * @return combined path
     */
    public static String combinePaths(String first, String... other) {
        return Paths.get(first, other).toString();
    }

    /**
     * Read a file from classpath
     *
     * @param fileName
     * @return byte[] data
     * @throws IOException
     */
    public static byte[] readFileFromClasspath(String fileName) throws IOException {
        InputStream is = Utils.class.getClassLoader().getResourceAsStream(fileName);
        byte[] data = ByteStreams.toByteArray(is);
        try {
            is.close();
        } catch (IOException ex) {
        }
        return data;
    }

    public static Properties parseGrpcUrl(String url) {
        if (StringUtil.isNullOrEmpty(url)) {
            throw new RuntimeException("URL cannot be null or empty");
        }

        Properties props = new Properties();
        Pattern p = Pattern.compile("([^:]+)[:]//([^:]+)[:]([0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(url);
        if (m.matches()) {
            props.setProperty("protocol", m.group(1));
            props.setProperty("host", m.group(2));
            props.setProperty("port", m.group(3));

            String protocol = props.getProperty("protocol");
            if (!"grpc".equals(protocol) && !"grpcs".equals(protocol)) {
                throw new RuntimeException(format("Invalid protocol expected grpc or grpcs and found %s.", protocol));
            }
        } else {
            throw new RuntimeException("URL must be of the format protocol://host:port. Found: '" + url + "'");
        }

        // TODO: allow all possible formats of the URL
        return props;
    }

    /**
     * Check if the strings Grpc url is valid
     *
     * @param url
     * @return Return the exception that indicates the error or null if ok.
     */
    public static Exception checkGrpcUrl(String url) {
        try {

            parseGrpcUrl(url);
            return null;

        } catch (Exception e) {
            return e;
        }
    }

    /**
     * Check if a string is null or empty.
     *
     * @param url the string to test.
     * @return {@code true} if the string is null or empty; otherwise {@code false}.
     */
    public static boolean isNullOrEmpty(String url) {
        return url == null || url.isEmpty();
    }

    private static final int NONONCE_LENGTH = 24;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] generateNonce() {

        byte[] values = new byte[NONONCE_LENGTH];
        RANDOM.nextBytes(values);

        return values;
    }

    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        return encodeHexString(bytes);

    }

    /**
     * Private constructor to prevent instantiation.
     */
    private Utils() {
    }

}
