package com.example.gh697;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.awspring.cloud.autoconfigure.core.CredentialsProperties;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.TestExecutionListeners;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@SpringBootTest
@Testcontainers
@TestExecutionListeners(
    value = Gh697ApplicationTests.SqsTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class Gh697ApplicationTests {

  @Container
  static LocalStackContainer localStackContainer =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.1.0"))
          .withServices(LocalStackContainer.Service.SQS);

  @Autowired private SqsAsyncClient sqsAsyncClient;

  @Value("${sqs.name}")
  private String queueName;

  @Test
  void test() {
    await()
        .atMost(Duration.ofSeconds(5L))
        .until(
            () ->
                sqsAsyncClient
                    .getQueueAttributes(
                        GetQueueAttributesRequest.builder()
                            .queueUrl(queueName)
                            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                            .build())
                    .join()
                    .attributes()
                    .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                    .equals("0"));
  }

  static class SqsTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestMethod(final TestContext testContext) throws JsonProcessingException {
      var applicationContext = testContext.getApplicationContext();
      var queueName = applicationContext.getEnvironment().getRequiredProperty("sqs.name");
      var sqsAsyncClient = applicationContext.getBean("sqsAsyncClient", SqsAsyncClient.class);

      var createQueueResponse =
          sqsAsyncClient
              .createQueue(CreateQueueRequest.builder().queueName(queueName).build())
              .join();

      var payload = new Gh697Application.MyPojo(new Random().nextLong(), OffsetDateTime.now());

      // Consuming this message will fail with a MessageConversionException.
      var sqsTemplate = applicationContext.getBean("sqsTemplate", SqsTemplate.class);
      sqsTemplate.send(createQueueResponse.queueUrl(), payload);

      // Consuming the message produced by the sqsAsyncClient works as expected.
      //      var objectMapper = applicationContext.getBean("objectMapper", ObjectMapper.class);
      //            sqsAsyncClient.sendMessage(
      //                SendMessageRequest.builder()
      //                    .queueUrl(createQueueResponse.queueUrl())
      //                    .messageBody(objectMapper.writeValueAsString(payload))
      //                    .build());
    }
  }

  @TestConfiguration
  static class SqsTestConfiguration {

    @Bean
    SqsAsyncClient sqsAsyncClient(final CredentialsProperties credentialsProperties) {
      return SqsAsyncClient.builder()
          .endpointOverride(localStackContainer.getEndpoint())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      credentialsProperties.getAccessKey(), credentialsProperties.getSecretKey())))
          .build();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().registerModule(new JavaTimeModule());
    }
  }
}
