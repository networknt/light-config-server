package com.networknt.configserver.model;

import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Objects;


public class ServiceConfig {
    @BsonProperty
    String name;
    @BsonProperty
    String content;

    @BsonCreator
    public ServiceConfig(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public ServiceConfig(){}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceConfig that = (ServiceConfig) o;
        return name.equals(that.name) &&
                content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, content);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Document toBson() {
        return new Document("name", getName()).append("content", getContent());
    }
}
