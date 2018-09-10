package de.catma.v10ui.frame;

import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.*;
import de.catma.document.repository.RepositoryProperties;
import de.catma.document.repository.RepositoryPropertyKey;
import de.catma.project.ProjectManager;
import de.catma.repository.LoginToken;
import de.catma.repository.db.maintenance.UserManager;
import de.catma.repository.git.GitProjectManager;
import de.catma.util.NonModifiableProperties;
import de.catma.v10ui.authentication.AuthenticationHandler;
import de.catma.v10ui.projects.ProjectManagerView;
import de.catma.v10ui.projects.ProjectTilesView;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


@HtmlImport("styles/shared-styles.html")
@Route("")
@PageTitle("CATMA 6-flow")
public class FrameView extends Div implements RouterLayout, PageConfigurator,LoginToken,HasComponents {

    private Object user;
    private ProjectManagerView projectManagerView;
    private Button btProjects;
    private Button btloginLogout;
    private Button btProject;
    private Button btTags;
    private Button btAnnotate;
    private Button btAnalyze;
    private  ProjectTilesView projectTilesView;

    private UserManager userManager = new UserManager();
    private HorizontalLayout mainContent;


    public FrameView(){
        initComponents();

    }
    private  void initComponents(){
        H2 title = new H2("CATMA_6");
        title.setClassName("main-layout__title");

        btloginLogout = new Button(("LoginLogout"),event ->  handleLoginLogoutEvent());
        btloginLogout.addClassName("main-layout__nav");

        btProjects = new Button("Projects");
        btProjects.setWidth("100%");
        btProjects.setClassName("main-layout_big_angular_btn");


        btProject= new Button("Project");

        btProject.setClassName("main-layout_menue_btn");
        btProject.getStyle().set("margin-top","25vh");//guarantees the distance to the top

        btTags= new Button("Tags");

        btTags.setClassName("main-layout_menue_btn");

        btAnnotate= new Button("Annotate");
        btAnnotate.setClassName("main-layout_menue_btn");

        btAnalyze = new Button("Analyze");
        btAnalyze.setClassName("main-layout_menue_btn");

        Div header = new Div(title,btloginLogout);
        header.addClassName("main-layout__header");

        mainContent = new HorizontalLayout();

        VerticalLayout leftPanel =new VerticalLayout();
        leftPanel.setClassName("main-layout__leftPanel");
        leftPanel.add(btProjects,btProject,btTags,btAnnotate,btAnalyze);

        mainContent.add(leftPanel);

        add(header);

    }
    private void initTempDirectory() throws IOException {
        String tempDirProp = RepositoryPropertyKey.TempDir.getValue();
        File tempDir = new File(tempDirProp);
        if ((!tempDir.exists() && !tempDir.mkdirs())) {
            throw new IOException("could not create temporary directory: " ); //$NON-NLS-1$
        }
    }


    private void handleLoginLogoutEvent() {

/*        String scheme = VaadinServletService.getCurrentServletRequest().getScheme();
        String serverName = VaadinServletService.getCurrentServletRequest().getServerName();
        Integer port = VaadinServletService.getCurrentServletRequest().getServerPort();
        String contextPath = VaadinService.getCurrentRequest().getContextPath();
        ServletConfig cfg = VaadinServlet.getCurrent().getServletConfig();*/

        if (this.user == null) {

            AuthenticationHandler authenticationHandler =
                    new AuthenticationHandler();

            authenticationHandler.authenticate(userIdentification -> {
                try {

                    this.user = userIdentification;
                    userManager.login(this);

                    // backgroundService = new UIBackgroundService(true);

                   initTempDirectory();

                    ProjectManager projectManager = new GitProjectManager(
                            RepositoryPropertyKey.GitBasedRepositoryBasePath.getValue(),
                            userIdentification
                    );

                    projectTilesView = new ProjectTilesView(projectManager);

                   // projectManagerView = new ProjectManagerView(projectManager);

                    add(mainContent);

                    mainContent.add(projectTilesView);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } else {

            VaadinSession.getCurrent().close();
            close();
        }
    }

    public void close() {
        VaadinSession.getCurrent().setAttribute("USER", null);
        if (projectTilesView != null) {
          remove(mainContent);
            userManager.logout(this);
        }
    }

    @Override
    public Object getUser() {
        return user;
    }

    @Override
    public void configurePage(InitialPageSettings settings) {
        settings.addMetaTag("apple-mobile-web-app-capable", "yes");
        settings.addMetaTag("apple-mobile-web-app-status-bar-style", "black");
    }
}