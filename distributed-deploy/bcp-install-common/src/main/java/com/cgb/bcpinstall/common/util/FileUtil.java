package com.cgb.bcpinstall.common.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public class FileUtil {
    public static String createTempPath() {
        String tmpPath = System.getProperty("java.io.tmpdir");
        if (!tmpPath.endsWith(File.separator)) {
            tmpPath = tmpPath + File.separator;
        }

        String rootPath = tmpPath + UUID.randomUUID().toString().replaceAll("-", "") + File.separator;
        FileUtil.makeFilePath(rootPath, true);

        return rootPath;
    }

    public static boolean writeTxtFile(String content, File fileName, String encoding) {
        FileOutputStream o = null;
        boolean result = false;
        try {
            o = new FileOutputStream(fileName, false);
            o.write(content.getBytes(encoding));
            result = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (o != null) {
                try {
                    o.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    public static String getFileContent(String filePath) {
        try {
            FileInputStream fis = new FileInputStream(filePath);
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            IOUtils.copy(fis, os);
            fis.close();
            String content = os.toString();
            os.close();

            return content;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String reviseDir(String dir) {
        if (dir.startsWith("../")) {
            dir = getUserDir() + dir;
        } else if (dir.startsWith("..")) {
            dir = getUserDir() + dir + File.separator;
        } else if (dir.startsWith("./")) {
            dir = dir.replace("./", getUserDir());
        } else if (dir.startsWith(".")) {
            dir = dir.replace(".", getUserDir()) + File.separator;
        }

        return dir;
    }

    public static String getUserDir() {
        String dirPath = System.getProperty("user.dir");
        dirPath = pathManipulation(dirPath);
        if (!dirPath.endsWith(File.separator)) {
            dirPath = dirPath + File.separator;
        }
        return dirPath;
    }

    public static void tarDecompression(String decompressFilePath, String resultDirPath) throws Exception {
        TarArchiveInputStream tais = null;
        FileInputStream fis = null;
        try {
            File file = new File(decompressFilePath);
            fis = new FileInputStream(file);
            tais = new TarArchiveInputStream(fis);

            TarArchiveEntry tae = null;
            while ((tae = tais.getNextTarEntry()) != null) {
                BufferedOutputStream bos = null;
                FileOutputStream fos = null;
                try {
                    String dir = resultDirPath + File.separator + tae.getName();// tar档中文件
                    File dirFile = new File(dir);
                    fos = new FileOutputStream(dirFile);
                    bos = new BufferedOutputStream(fos);
                    int count;
                    byte[] data = new byte[1024];
                    while ((count = tais.read(data, 0, 1024)) != -1) {
                        bos.write(data, 0, count);
                    }
                } finally {
                    if (bos != null)
                        bos.close();
                    if (fos != null)
                        fos.close();
                }
            }
        } finally {
            if (tais != null)
                tais.close();
            if (fis != null)
                fis.close();
        }
    }

    public static void tarDecompression(InputStream inputStream, String resultDirPath) throws Exception {
        TarArchiveInputStream tais = null;
        try {
            tais = new TarArchiveInputStream(inputStream);

            TarArchiveEntry tae = null;
            while ((tae = tais.getNextTarEntry()) != null) {
                BufferedOutputStream bos = null;
                FileOutputStream fos = null;
                try {
                    String dir = resultDirPath + File.separator + tae.getName();// tar档中文件
                    File dirFile = new File(dir);
                    fos = new FileOutputStream(dirFile);
                    bos = new BufferedOutputStream(fos);
                    int count;
                    byte[] data = new byte[1024];
                    while ((count = tais.read(data, 0, 1024)) != -1) {
                        bos.write(data, 0, count);
                    }
                } finally {
                    if (bos != null)
                        bos.close();
                    if (fos != null)
                        fos.close();
                }
            }
        } finally {
            if (tais != null)
                tais.close();
        }
    }

    public static boolean tarCompression(String[] filesPathArray, String resultFilePath) throws Exception {
        FileOutputStream fos = null;
        TarArchiveOutputStream taos = null;
        try {
            fos = new FileOutputStream(new File(resultFilePath));
            taos = new TarArchiveOutputStream(fos);
            for (String filePath : filesPathArray) {
                BufferedInputStream bis = null;
                FileInputStream fis = null;
                try {
                    File file = new File(filePath);
                    TarArchiveEntry tae = new TarArchiveEntry(file);
                    tae.setName(new String(file.getName().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                    taos.putArchiveEntry(tae);
                    fis = new FileInputStream(file);
                    bis = new BufferedInputStream(fis);
                    int count;
                    byte data[] = new byte[1024];
                    while ((count = bis.read(data, 0, 1024)) != -1) {
                        taos.write(data, 0, count);
                    }
                } finally {
                    taos.closeArchiveEntry();
                    if (bis != null)
                        bis.close();
                    if (fis != null)
                        fis.close();
                }
            }
        } finally {
            if (taos != null)
                taos.close();
            if (fos != null)
                fos.close();

        }

        return true;
    }

    public static void gzipCompression(String filePath, String resultFilePath) throws IOException {
        InputStream fin = null;
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        GzipCompressorOutputStream gcos = null;
        try {
            fin = Files.newInputStream(Paths.get(filePath));
            bis = new BufferedInputStream(fin);
            fos = new FileOutputStream(resultFilePath);
            bos = new BufferedOutputStream(fos);
            gcos = new GzipCompressorOutputStream(bos);
            byte[] buffer = new byte[1024];
            int read = -1;
            while ((read = bis.read(buffer)) != -1) {
                gcos.write(buffer, 0, read);
            }
        } finally {
            if (gcos != null)
                gcos.close();
            if (bos != null)
                bos.close();
            if (fos != null)
                fos.close();
            if (bis != null)
                bis.close();
            if (fin != null)
                fin.close();
        }
    }

    public static void tarGzCompression(String sourcePath, String resultFilePath) throws IOException {
        File sourceDirectory = new File(sourcePath);

        FileOutputStream fos = new FileOutputStream(resultFilePath, false);

        TarArchiveOutputStream archiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(fos));
        archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

        try {
            Collection<File> childrenFiles = org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true);

            ArchiveEntry archiveEntry;
            FileInputStream fileInputStream;
            for (File childFile : childrenFiles) {
                String childPath = childFile.getAbsolutePath();
                String relativePath = FilenameUtils.separatorsToUnix(childPath.substring((sourcePath.length() + 1)));

                relativePath = Utils.combinePaths(sourceDirectory.getName(), relativePath);

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
        } finally {
            IOUtils.closeQuietly(archiveOutputStream);
            IOUtils.closeQuietly(fos);
        }
    }

    public static void gzipDecompression(String compressedFilePath, String resultDirPath) throws IOException {
        InputStream fin = null;
        BufferedInputStream in = null;
        OutputStream out = null;
        GzipCompressorInputStream gcis = null;
        try {
            out = Files.newOutputStream(Paths.get(resultDirPath));
            fin = Files.newInputStream(Paths.get(compressedFilePath));
            in = new BufferedInputStream(fin);
            gcis = new GzipCompressorInputStream(in);

            IOUtils.copy(gcis, out);
        } finally {
            if (gcis != null)
                gcis.close();
            if (in != null)
                in.close();
            if (fin != null)
                fin.close();
            if (out != null)
                out.close();
        }
    }

    public static void gzipDecompression(InputStream inputStream, String resultDirPath) throws IOException {
        BufferedInputStream in = null;
        OutputStream out = null;
        GzipCompressorInputStream gcis = null;
        try {
            out = Files.newOutputStream(Paths.get(resultDirPath));
            in = new BufferedInputStream(inputStream);
            gcis = new GzipCompressorInputStream(in);
            final byte[] buffer = new byte[1024];
            int n = 0;
            while (-1 != (n = gcis.read(buffer))) {
                out.write(buffer, 0, n);
            }
        } finally {
            if (gcis != null)
                gcis.close();
            if (in != null)
                in.close();
            if (out != null)
                out.close();
        }
    }

    public static boolean tarGzDecompress(String srcFilePath, String targetPath, boolean renewDestPath) {
        FileInputStream is = null;
        try {
            is = new FileInputStream(srcFilePath);
            return tarGzDecompress(is, targetPath, renewDestPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(is);
        }
        return false;
    }

    public static boolean tarGzDecompress(InputStream inputStream, String targetPath, boolean renewDestPath) {
        TarArchiveInputStream tarIn = null;
        OutputStream out = null;
        GZIPInputStream gzipIn = null;

        String outPath = targetPath;
        if (!outPath.endsWith(File.separator)) {
            outPath = outPath + File.separator;
        }
        try {
            gzipIn = new GZIPInputStream(inputStream);
            tarIn = new TarArchiveInputStream(gzipIn, 1024 * 2);

            // 创建输出目录
            makeFilePath(targetPath, renewDestPath);

            ArchiveEntry entry = null;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) { // 是目录
                    makeFilePath(outPath + entry.getName(), false);
                } else { // 是文件
                    File tempFile = new File(outPath + entry.getName());
                    makeFilePath(tempFile.getParent() + File.separator, false);

                    out = new FileOutputStream(tempFile);

                    IOUtils.copy(tarIn, out);

                    out.flush();
                }
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (tarIn != null) {
                    tarIn.close();
                }
                if (gzipIn != null) {
                    gzipIn.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public static boolean tarGzDecompress(InputStream inputStream, String targetPath, String removePathPrefix) {
        TarArchiveInputStream tarIn = null;
        OutputStream out = null;
        GZIPInputStream gzipIn = null;

        String outPath = targetPath;
        if (!outPath.endsWith(File.separator)) {
            outPath = outPath + File.separator;
        }
        try {
            gzipIn = new GZIPInputStream(inputStream);
            tarIn = new TarArchiveInputStream(gzipIn, 1024 * 2);

            // 创建输出目录
            makeFilePath(targetPath, true);

            ArchiveEntry entry = null;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isDirectory()) { // 是目录
                    makeFilePath(outPath + entry.getName(), false);
                } else { // 是文件
                    String entryName = entry.getName();
                    if (removePathPrefix != null && removePathPrefix.length() > 0) {
                        if (entryName.startsWith(removePathPrefix)) {
                            entryName = entryName.substring(removePathPrefix.length());
                            if (entryName.startsWith(File.separator)) {
                                entryName = entryName.substring(1);
                            }
                        }
                    }
                    File tempFile = new File(outPath + entryName);
                    makeFilePath(tempFile.getParent() + File.separator, false);
                    out = new FileOutputStream(tempFile);
                    int len = 0;
                    byte[] b = new byte[2048];

                    while ((len = tarIn.read(b)) != -1) {
                        out.write(b, 0, len);
                    }
                    out.flush();
                }
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (tarIn != null) {
                    tarIn.close();
                }
                if (gzipIn != null) {
                    gzipIn.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public static String tarGzGetPathPrefix(InputStream inputStream) {
        GZIPInputStream gzipIn = null;
        TarArchiveInputStream tarIn = null;

        try {
            gzipIn = new GZIPInputStream(inputStream);
            tarIn = new TarArchiveInputStream(gzipIn, 1024 * 2);

            ArchiveEntry entry = null;
            String relativePath = null;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (!entry.isDirectory()) { // 是目录
                    String entryName = entry.getName();
                    String path = entryName.substring(0, entryName.lastIndexOf(File.separator) + 1);
                    if (relativePath == null) {
                        relativePath = path;
                    } else {
                        int i = 0;
                        while (i < relativePath.length() && i < path.length()) {
                            if (relativePath.charAt(i) != path.charAt(i)) {
                                break;
                            }
                            i++;
                        }
                        relativePath = relativePath.substring(0, i);
                        if (!relativePath.endsWith(File.separator)) {
                            relativePath = relativePath.substring(relativePath.lastIndexOf(File.separator) + 1);
                        }
                    }
                }
            }

            return relativePath;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (tarIn != null) {
                    tarIn.close();
                }
                if (gzipIn != null) {
                    gzipIn.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static boolean makeFilePath(String filePath, boolean renew) {
        File f = new File(filePath);

        if (renew) {
            rmFile(f);
        }

        if (f.exists()) {
            return true;
        }

        return f.mkdirs();
    }

    public static boolean rmFile(File file) {
        if (!file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File f : files) {
                rmFile(f);
            }
        }

        return file.delete();
    }

    public static String pathManipulation(String path) {
        HashMap<String, String> map = new HashMap<String, String>(16);
        map.put("a", "a");
        map.put("b", "b");
        map.put("c", "c");
        map.put("d", "d");
        map.put("e", "e");
        map.put("f", "f");
        map.put("g", "g");
        map.put("h", "h");
        map.put("i", "i");
        map.put("j", "j");
        map.put("k", "k");
        map.put("l", "l");
        map.put("m", "m");
        map.put("n", "n");
        map.put("o", "o");
        map.put("p", "p");
        map.put("q", "q");
        map.put("r", "r");
        map.put("s", "s");
        map.put("t", "t");
        map.put("u", "u");
        map.put("v", "v");
        map.put("w", "w");
        map.put("x", "x");
        map.put("y", "y");
        map.put("z", "z");

        map.put("A", "A");
        map.put("B", "B");
        map.put("C", "C");
        map.put("D", "D");
        map.put("E", "E");
        map.put("F", "F");
        map.put("G", "G");
        map.put("H", "H");
        map.put("I", "I");
        map.put("J", "J");
        map.put("K", "K");
        map.put("L", "L");
        map.put("M", "M");
        map.put("N", "N");
        map.put("O", "O");
        map.put("P", "P");
        map.put("Q", "Q");
        map.put("R", "R");
        map.put("S", "S");
        map.put("T", "T");
        map.put("U", "U");
        map.put("V", "V");
        map.put("W", "W");
        map.put("X", "X");
        map.put("Y", "Y");
        map.put("Z", "Z");

        map.put(":", ":");
        map.put("/", "/");
        map.put("\\", "\\");
        map.put(".", ".");
        map.put("-", "-");
        map.put("_", "_");
        map.put("~", "~");

        map.put("0", "0");
        map.put("1", "1");
        map.put("2", "2");
        map.put("3", "3");
        map.put("4", "4");
        map.put("5", "5");
        map.put("6", "6");
        map.put("7", "7");
        map.put("8", "8");
        map.put("9", "9");

        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            if (map.get(path.charAt(i) + "") != null) {
                temp.append(map.get(path.charAt(i) + ""));
            }
        }
        return temp.toString();

    }
}
