/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.service.load;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baidu.hugegraph.entity.load.FileMapping;
import com.baidu.hugegraph.entity.load.FileSetting;
import com.baidu.hugegraph.entity.load.FileUploadResult;
import com.baidu.hugegraph.exception.InternalException;
import com.baidu.hugegraph.mapper.load.FileMappingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class FileMappingService {

    @Autowired
    private FileMappingMapper mapper;

    public FileMapping get(int id) {
        return this.mapper.selectById(id);
    }

    public List<FileMapping> listAll() {
        return this.mapper.selectList(null);
    }

    public IPage<FileMapping> list(int connId, int pageNo, int pageSize) {
        QueryWrapper<FileMapping> query = Wrappers.query();
        query.eq("conn_id", connId).orderByDesc("name");
        Page<FileMapping> page = new Page<>(pageNo, pageSize);
        return this.mapper.selectPage(page, query);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int save(FileMapping mapping) {
        return this.mapper.insert(mapping);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int update(FileMapping mapping) {
        return this.mapper.updateById(mapping);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int remove(int id) {
        return this.mapper.deleteById(id);
    }

    public FileUploadResult uploadFile(int connId, MultipartFile srcFile,
                                       File destFile) {
        String fileName = srcFile.getOriginalFilename();
        log.debug("Upload file {} length {}", fileName, srcFile.getSize());

        FileUploadResult result = new FileUploadResult();
        result.setName(fileName);
        result.setSize(srcFile.getSize());
        try (InputStream is = srcFile.getInputStream();
             OutputStream os = new FileOutputStream(destFile)) {
            IOUtils.copy(is, os);

            // Save file mapping
            FileMapping mapping = new FileMapping(connId, fileName,
                                                  destFile.getPath());
            if (this.save(mapping) != 1) {
                // Delete current upload file
                FileUtils.forceDelete(destFile);
                throw new InternalException("entity.insert.failed", mapping);
            }
            // Get file mapping id
            result.setId(mapping.getId());
            result.setStatus(FileUploadResult.Status.SUCCESS);
        } catch (Exception e) {
            log.error("Failed to save upload file and insert file mapping " +
                      "record", e);
            result.setStatus(FileUploadResult.Status.FAILURE);
            result.setCause(e.getMessage());
        }
        return result;
    }

    public void tryMergePartFiles(File dir, int total) {
        File[] partFiles = dir.listFiles();
        if (partFiles == null) {
            throw new InternalException("The part files can't be null");
        }
        if (partFiles.length != total) {
            return;
        }

        File newFile = new File(dir.getAbsolutePath() + ".all");
        File destFile = new File(dir.getAbsolutePath());
        if (partFiles.length == 1) {
            // Rename file to dest file
            try {
                FileUtils.moveFile(partFiles[0], newFile);
            } catch (IOException e) {
                throw new InternalException("load.upload.move-file.failed");
            }
        } else {
            try (OutputStream os = new FileOutputStream(newFile, true)) {
                for (int i = 0; i < partFiles.length; i++) {
                    File partFile = partFiles[i];
                    try (InputStream is = new FileInputStream(partFile)) {
                        IOUtils.copy(is, os);
                    } catch (IOException e) {
                        throw new InternalException(
                                  "load.upload.merge-file.failed");
                    }
                }
            } catch (IOException e) {
                throw new InternalException("load.upload.merge-file.failed");
            }
        }
        // Delete origin directory
        try {
            FileUtils.forceDelete(dir);
        } catch (IOException e) {
            throw new InternalException("load.upload.delete-temp-dir.failed");
        }
        // Rename file to dest file
        if (!newFile.renameTo(destFile)) {
            throw new InternalException("load.upload.rename-file.failed");
        }
    }

    public void extractColumns(FileMapping mapping) {
        File file = FileUtils.getFile(mapping.getPath());
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new InternalException("The file '%s' is not found", file);
        }
        FileSetting setting = mapping.getFileSetting();
        String delimiter = setting.getDelimiter();

        String[] columnNames;
        String[] columnValues;
        try {
            String line = reader.readLine();
            String[] firstLine = StringUtils.split(line, delimiter);
            if (setting.isHasHeader()) {
                // The first line as column names
                columnNames = firstLine;
                // The second line as column values
                line = reader.readLine();
                columnValues = StringUtils.split(line, delimiter);
            } else {
                // Let columns names as: column-1, column-2 ...
                columnNames = new String[firstLine.length];
                for (int i = 1; i <= firstLine.length; i++) {
                    columnNames[i - 1] = "col-" + i;
                }
                // The first line as column values
                columnValues = firstLine;
            }
        } catch (IOException e) {
            throw new InternalException("Failed to read header and sample " +
                                        "data from file '%s'", file);
        } finally {
            IOUtils.closeQuietly(reader);
        }

        setting.setColumnNames(Arrays.asList(columnNames));
        setting.setColumnValues(Arrays.asList(columnValues));
    }
}