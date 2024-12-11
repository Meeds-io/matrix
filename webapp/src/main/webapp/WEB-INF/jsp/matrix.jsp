<%@page import="org.exoplatform.commons.utils.PropertyManager"%>
<%@page import="io.meeds.chat.service.MatrixService"%>
<%@page import="io.meeds.chat.service.utils.MatrixConstants"%>
<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page import="org.exoplatform.commons.utils.CommonsUtils"%>
<%
String matrixRestrictedGroup = PropertyManager.getProperty(MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP);
MatrixService matrixService = CommonsUtils.getService(MatrixService.class);
if (StringUtils.isBlank(matrixRestrictedGroup) || matrixService.isUserMemberOfGroup(user.getUserName(), matrixRestrictedGroup)) {
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