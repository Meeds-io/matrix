<%@page import="org.exoplatform.commons.utils.PropertyManager"%>
<%@page import="io.meeds.chat.service.utils.MatrixConstants"%>
<%@page import="io.meeds.chat.service.MatrixService"%>
<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page import="org.exoplatform.commons.utils.CommonsUtils"%>
<%@page import="org.exoplatform.portal.config.UserACL"%>
<%@page import="org.exoplatform.services.security.ConversationState"%>
<%@page import="org.exoplatform.services.security.Identity"%>
<%
String matrixRestrictedGroup = PropertyManager.getProperty(MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP);
UserACL userACL = CommonsUtils.getService(UserACL.class);
MatrixService matrixService = CommonsUtils.getService(MatrixService.class);
Identity userIdentity = ConversationState.getCurrent().getIdentity();

if (matrixService.isServiceAvailable() && (StringUtils.isBlank(matrixRestrictedGroup) || userACL.isUserInGroup(userIdentity, matrixRestrictedGroup))) {
%>
<div class="VuetifyApp">
  <div data-app="true"
    class="v-application v-application--is-ltr theme--light"
    id="matrixChatButton">
    <script type="text/javascript">
      require(['PORTLET/matrix/Matrix'], app => {
        const serverName = '<%= PropertyManager.getProperty(io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME)%>';
        app.init(serverName);
      });
    </script>
  </div>
</div>
<%}%>
