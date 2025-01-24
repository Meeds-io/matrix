package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.service.utils.MatrixHttpClient;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(MatrixBaseTest.class)
class MatrixServiceTest extends MatrixBaseTest {

  @MockBean
  MatrixHttpClient matrixHttpClient;

  @Autowired
  MatrixService matrixService;

  @Test
  void init() {
    try {
      this.matrixService.init();
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void updateUserDisplayName() {
  }
}
