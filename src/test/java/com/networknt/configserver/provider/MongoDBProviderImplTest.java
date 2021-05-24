package com.networknt.configserver.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.networknt.configserver.model.ServiceConfig;
import org.bson.Document;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class  MongoDBProviderImplTest {
  String configKey = "files/example/globals/0.0.1/dev";
  MongoClient mongoClient = MongoClients.create("mongodb://root:example@0.0.0.0:27017");
  MongoDatabase database = mongoClient.getDatabase("configServer");
  MongoCollection<Document> collection = database.getCollection("configs").withCodecRegistry(fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
          fromProviders(PojoCodecProvider.builder().automatic(true).build())));

  @Test
  public void testInsert() throws IOException {
    Document insert = new Document("_id", configKey);
    List<Document> collect = Files.list(FileSystems.getDefault().getPath("src/test/resources/config")).map(path -> {
      try {
        String content = Files.readAllLines(path).stream().collect(Collectors.joining());
        return new Document("configName", path.getFileName().toString()).append("content", content);
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }).filter(Objects::nonNull).collect(Collectors.toList());
    insert.append("configs", collect);
    // delete the original record to avoid duplication.
    Bson filter = eq("_id", configKey);
    DeleteResult result = collection.deleteOne(filter);
    collection.insertOne(insert);
  }

  @Test
  public void testUpdate() throws IOException {

    Document insert = new Document("_id", configKey);
    List<Document> collect = Files.list(FileSystems.getDefault().getPath("src/test/resources/config")).map(path -> {
      try {
        String content = Files.readAllLines(path).stream().collect(Collectors.joining());
        ServiceConfig serviceConfig = new ServiceConfig("server.yml", content);
        return serviceConfig.toBson();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }).filter(Objects::nonNull).collect(Collectors.toList());
      collection.updateOne(eq(configKey), set("a", "b"), new UpdateOptions().upsert(true));
      insert.append("configs", collect);
      collection.updateOne(Filters.eq(configKey), set("configs", collect), new UpdateOptions().upsert(true));
  }

  @Test
  public void testFind() {
    String configKey = "files/example/0.0.1/example-service/0.0.1/dev";
    Map<String, Object> configsMap = new HashMap<>();
    Document document = new Document("_id", configKey);
    Document entry;
    entry = collection.find(document).first();
    if (entry != null && !entry.isEmpty()) {
      List<Document> configs = ((List)entry.get("configs"));
      configs.forEach(config -> {
        byte[] content = config.getString("content").getBytes();
        String encodedContent = Base64.getMimeEncoder().encodeToString(content);
        configsMap.put(config.getString("configName"), encodedContent);
      });
    }
  }

  @Test
  public void testDeserialize() {
    String json = "{\n" +
            "        \"name\": \"body.yml\",\n" +
            "        \"content\": \"a: b\"\n" +
            "    }";
    Map<String, Object> map = new HashMap<>();
    map.put("name", "body.yml");
    map.put("content", "a:b");
    ObjectMapper mapper = new ObjectMapper();
    ServiceConfig serviceConfig = mapper.convertValue(map, ServiceConfig.class);
    System.out.println(serviceConfig);
  }
}
