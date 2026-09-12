package com.ff.mycloud.common.util;

/** 单段 HTTP bytes Range。项目暂不支持 multipart/byteranges。 */
public record HttpRange(long start, long end) {

    public long length() {
        return end - start + 1;
    }

    public static HttpRange parse(String header, long size) {
        if (size < 0 || header == null || !header.startsWith("bytes=")) {
            throw new IllegalArgumentException("无效的 Range 请求");
        }
        String value = header.substring(6).trim();
        if (value.isEmpty() || value.contains(",")) {
            throw new IllegalArgumentException("仅支持单段 Range 请求");
        }
        int dash = value.indexOf('-');
        if (dash < 0) {
            throw new IllegalArgumentException("无效的 Range 请求");
        }
        try {
            String left = value.substring(0, dash).trim();
            String right = value.substring(dash + 1).trim();
            if (left.isEmpty()) {
                long suffixLength = Long.parseLong(right);
                if (suffixLength <= 0 || size == 0) throw new IllegalArgumentException();
                return new HttpRange(Math.max(0, size - suffixLength), size - 1);
            }
            long start = Long.parseLong(left);
            if (start < 0 || start >= size) throw new IllegalArgumentException();
            long end = right.isEmpty() ? size - 1 : Long.parseLong(right);
            if (end < start) throw new IllegalArgumentException();
            return new HttpRange(start, Math.min(end, size - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的 Range 请求", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("请求范围超出文件大小", e);
        }
    }
}
