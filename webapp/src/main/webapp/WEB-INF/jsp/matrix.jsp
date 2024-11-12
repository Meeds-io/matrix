<%@page import="org.exoplatform.commons.utils.PropertyManager"%>

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
