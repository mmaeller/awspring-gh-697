package com.example.gh697;

import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class Gh697Application {

  private static final Logger LOG = LoggerFactory.getLogger(Gh697Application.class);

  public static void main(String[] args) {
    SpringApplication.run(Gh697Application.class, args);
  }

  @Component
  static class DataSqsListener {

    @SqsListener("${sqs.name}")
    public void consume(@Payload final MyPojo myPojo) {
      LOG.info("{}", myPojo);
    }
  }

  record MyPojo(Long id, OffsetDateTime createdAt) {}
}
