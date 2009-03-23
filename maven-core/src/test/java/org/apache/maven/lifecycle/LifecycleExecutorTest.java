package org.apache.maven.lifecycle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.model.Repository;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.DeprecationEventDispatcher;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.MavenEvents;
import org.apache.maven.plugin.MavenPluginCollector;
import org.apache.maven.plugin.MavenPluginDiscoverer;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;

public class LifecycleExecutorTest
    extends PlexusTestCase
{
    @Requirement
    private MavenProjectBuilder projectBuilder;

    @Requirement
    private RepositorySystem repositorySystem;

    @Requirement
    private PluginManager pluginManager;

    @Requirement
    private DefaultLifecycleExecutor lifecycleExecutor;

    File pom;
    File targetPom;
    
    protected void setUp()
        throws Exception
    {
        projectBuilder = lookup( MavenProjectBuilder.class );
        repositorySystem = lookup( RepositorySystem.class );
        pluginManager = lookup( PluginManager.class );
        lifecycleExecutor = (DefaultLifecycleExecutor) lookup( LifecycleExecutor.class );
        targetPom = new File( getBasedir(), "target/lifecycle-executor/pom-plugin.xml" );

        if ( !targetPom.exists() )
        {
            pom = new File( getBasedir(), "src/test/pom.xml" );
            FileUtils.copyFile( pom, targetPom );
        }
    }

    public void testLifecyclePhases()
    {
        assertNotNull( lifecycleExecutor.getLifecyclePhases() );
    }

    // -----------------------------------------------------------------------------------------------
    // Tests which exercise the lifecycle executor when it is dealing with default lifecycle phases.
    // -----------------------------------------------------------------------------------------------
    
    public void testLifecycleQueryingUsingADefaultLifecyclePhase()
        throws Exception
    {        
    }    
    
    public void testLifecycleExecutionUsingADefaultLifecyclePhase()
        throws Exception
    {
        String base = "projects/lifecycle-executor/project-with-additional-lifecycle-elements";
        File sourceDirectory = new File( getBasedir(), "src/test/" + base );
        File targetDirectory = new File( getBasedir(), "target/" + base );
        FileUtils.copyDirectoryStructure( sourceDirectory, targetDirectory );
        File targetPom = new File( targetDirectory, "pom.xml" );        
        
        MavenSession session = createMavenSession( targetPom );
        
        assertEquals( "project-with-additional-lifecycle-elements", session.getCurrentProject().getArtifactId() );
        assertEquals( "1.0-SNAPSHOT", session.getCurrentProject().getVersion() );                
                
        lifecycleExecutor.execute( session );
    }
    
    // -----------------------------------------------------------------------------------------------
    // Tests which exercise the lifecycle executor when it is dealing with individual goals.
    // -----------------------------------------------------------------------------------------------
    
    public void testRemoteResourcesPlugin()
        throws Exception
    {
        MavenSession session = createMavenSession( targetPom );       

        String pluginArtifactId = "remote-resources";
        String goal = "process";
        MojoDescriptor mojoDescriptor = lifecycleExecutor.getMojoDescriptor( pluginArtifactId + ":" + goal, session, session.getCurrentProject() );

        PluginDescriptor pd = mojoDescriptor.getPluginDescriptor();
        assertNotNull( pd );
        assertEquals( "org.apache.maven.plugins", pd.getGroupId() );
        assertEquals( "maven-remote-resources-plugin", pd.getArtifactId() );
        assertEquals( "1.0", pd.getVersion() );

        MojoExecution mojoExecution = new MojoExecution( mojoDescriptor );

        pluginManager.executeMojo( session.getCurrentProject(), mojoExecution, session );
    }

    public void testSurefirePlugin()
        throws Exception
    {
        MavenSession session = createMavenSession( targetPom );       

        String pluginArtifactId = "surefire";
        String goal = "test";
        MojoDescriptor mojoDescriptor = lifecycleExecutor.getMojoDescriptor( pluginArtifactId + ":" + goal, session, session.getCurrentProject() );
        assertNotNull( mojoDescriptor );
        
        PluginDescriptor pd = mojoDescriptor.getPluginDescriptor();
        assertNotNull( pd );
        assertEquals( "org.apache.maven.plugins", pd.getGroupId() );
        assertEquals( "maven-surefire-plugin", pd.getArtifactId() );
        assertEquals( "2.4.2", pd.getVersion() );

        MojoExecution mojoExecution = new MojoExecution( mojoDescriptor );

        pluginManager.executeMojo( session.getCurrentProject(), mojoExecution, session );
    }
    
    // -----------------------------------------------------------------------------------------------
    // Testing help
    // -----------------------------------------------------------------------------------------------

    /**
     * We need to customize the standard Plexus container with the plugin discovery listener which
     * is what looks for the META-INF/maven/plugin.xml resources that enter the system when a
     * Maven plugin is loaded.
     * 
     * We also need to customize the Plexus container with a standard plugin discovery listener
     * which is the MavenPluginCollector. When a Maven plugin is discovered the MavenPluginCollector
     * collects the plugin descriptors which are found. 
     */
    protected void customizeContainerConfiguration( ContainerConfiguration containerConfiguration )
    {
        containerConfiguration.addComponentDiscoverer( new MavenPluginDiscoverer() );
        containerConfiguration.addComponentDiscoveryListener( new MavenPluginCollector() );
    }
    
    //!!jvz The repository system needs to know about the defaults for Maven, it's tied up in the embedder right now.
    protected ArtifactRepository getLocalRepository()
        throws InvalidRepositoryException
    {
        return repositorySystem.createLocalRepository( new File( "/Users/jvanzyl/.m2/repository" ) );
    }

    protected MavenSession createMavenSession( File pom )
        throws Exception
    {
        ArtifactRepository localRepository = getLocalRepository();

        Repository repository = new Repository();
        repository.setUrl( "http://repo1.maven.org/maven2" );
        repository.setId( "central" );

        ProjectBuilderConfiguration configuration = new DefaultProjectBuilderConfiguration()
            .setLocalRepository( localRepository )
            .setRemoteRepositories( Arrays.asList( repositorySystem.buildArtifactRepository( repository ) ) );

        MavenProject project = projectBuilder.build( pom, configuration );

        MavenExecutionRequest request = new DefaultMavenExecutionRequest()
            .setProjectPresent( true )
            .setPluginGroups( Arrays.asList( new String[] { "org.apache.maven.plugins" } ) )
            .setLocalRepository( localRepository )
            .setRemoteRepositories( Arrays.asList( repositorySystem.buildArtifactRepository( repository ) ) )
            .setGoals( Arrays.asList( new String[] { "package" } ) )    
            .addEventMonitor( new DefaultEventMonitor( new ConsoleLogger( 0, "" ) ) )
            .setProperties( new Properties() );

        List projects = new ArrayList();
        projects.add( project );
        
        ReactorManager reactorManager = new ReactorManager( projects, request.getReactorFailureBehavior() );
        
        EventDispatcher dispatcher = new DeprecationEventDispatcher( MavenEvents.DEPRECATIONS, request.getEventMonitors() );        
        
        MavenSession session = new MavenSession( getContainer(), request, reactorManager, dispatcher );
        //!!jvz This is not really quite right, take a look at how this actually works.
        session.setCurrentProject( project );
        
        return session;
    }
}
