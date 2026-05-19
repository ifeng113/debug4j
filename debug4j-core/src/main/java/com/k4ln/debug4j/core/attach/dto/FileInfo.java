package com.k4ln.debug4j.core.attach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.RandomAccessFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /**
     * 临时文件
     */
    private RandomAccessFile randomAccessFile;

    /**
     * 文件目录
     */
    private String fileDir;

    /**
     * 临时文件名
     */
    private String temporaryFilename;

    /**
     * 文件名称
     */
    private String filename;

}
