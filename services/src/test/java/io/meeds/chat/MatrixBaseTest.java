package io.meeds.chat;

import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.kernel.test.AbstractSpringTest;
import io.meeds.kernel.test.KernelExtension;
import io.meeds.spring.AvailableIntegration;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.component.test.KernelBootstrap;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@ExtendWith({ SpringExtension.class, KernelExtension.class })
@SpringBootApplication(scanBasePackages = { MatrixBaseTest.MODULE_NAME, AvailableIntegration.KERNEL_TEST_MODULE,
    AvailableIntegration.JPA_MODULE, AvailableIntegration.LIQUIBASE_MODULE, AvailableIntegration.WEB_MODULE, })
@EnableJpaRepositories(basePackages = MatrixBaseTest.MODULE_NAME)
@TestPropertySource(properties = { "spring.liquibase.change-log=" + MatrixBaseTest.CHANGELOG_PATH,
    "spring.profiles.active=matrix", })
@ConfiguredBy({ @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/matrix-test-configuration.xml"), })
public class MatrixBaseTest extends AbstractSpringTest {

  public static final String     MODULE_NAME    = "io.meeds.chat";

  private static KernelBootstrap bootstrap;

  public static final String     CHANGELOG_PATH = "classpath:db/changelog/matrix-rdbms.db.changelog-master.xml";

  @BeforeAll
  static void beforeAll() {
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "ThisIsAJWTSecretOfMatrixForTestingPurposes");
    PropertyManager.setProperty(MATRIX_SERVER_URL, "https://matrix.exo.tn");
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.exo.tn");
  }

  public PortalContainer getContainer() {
    return bootstrap == null ? bootContainer() : bootstrap.getContainer();
  }

  protected PortalContainer bootContainer() {
    PortalContainer container = bootContainer(getClass());
    ExoContainerContext.setCurrentContainer(container);
    return container;
  }

  protected void begin() {
    PortalContainer container = getContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }
}
