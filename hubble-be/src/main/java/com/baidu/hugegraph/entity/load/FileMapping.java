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

package com.baidu.hugegraph.entity.load;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.baidu.hugegraph.handler.EdgeMappingTypeHandler;
import com.baidu.hugegraph.handler.VertexMappingTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@TableName(value = "file_mapping", autoResultMap = true)
public class FileMapping {

    @TableId(type = IdType.AUTO)
    @JsonProperty("id")
    private Integer id;

    @TableField("conn_id")
    @JsonIgnore
    private Integer connId;

    @TableField("name")
    @JsonProperty("name")
    private String name;

    @TableField("path")
    @JsonIgnore
    private String path;

    @TableField(value = "file_setting", typeHandler = JacksonTypeHandler.class)
    @JsonProperty("file_setting")
    private FileSetting fileSetting;

    @TableField(value = "vertex_mappings",
                typeHandler = VertexMappingTypeHandler.class)
    @JsonProperty("vertex_mappings")
    private Map<String, VertexMapping> vertexMappings;

    @TableField(value = "edge_mappings",
                typeHandler = EdgeMappingTypeHandler.class)
    @JsonProperty("edge_mappings")
    private Map<String, EdgeMapping> edgeMappings;

    @TableField(value = "load_parameter",
                typeHandler = JacksonTypeHandler.class)
    @JsonProperty("load_parameter")
    private LoadParameter loadParameter;

    @TableField("last_access_time")
    @JsonProperty("last_access_time")
    private Date lastAccessTime;

    public FileMapping(int connId, String name, String path) {
        this(connId, name, path, new Date());
    }

    public FileMapping(int connId, String name, String path,
                       Date lastAccessTime) {
        this.id = null;
        this.connId = connId;
        this.name = name;
        this.path = path;
        this.fileSetting = new FileSetting();
        this.vertexMappings = new LinkedHashMap<>();
        this.edgeMappings = new LinkedHashMap<>();
        this.loadParameter = new LoadParameter();
        this.lastAccessTime = lastAccessTime;
    }
}
