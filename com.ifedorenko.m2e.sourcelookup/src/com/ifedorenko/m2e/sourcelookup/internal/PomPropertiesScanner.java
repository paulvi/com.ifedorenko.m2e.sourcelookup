/*******************************************************************************
 * Copyright (c) 2012 Igor Fedorenko
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Igor Fedorenko - initial API and implementation
 *******************************************************************************/
package com.ifedorenko.m2e.sourcelookup.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.codehaus.plexus.util.IOUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.internal.index.IIndex;
import org.eclipse.m2e.core.internal.index.IndexedArtifactFile;
import org.eclipse.m2e.core.internal.index.nexus.CompositeIndex;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;

@SuppressWarnings( "restriction" )
public abstract class PomPropertiesScanner<T>
{
    IMavenProjectRegistry projectRegistry = MavenPlugin.getMavenProjectRegistry();

    private static final MetaInfMavenScanner<Properties> SCANNER = new MetaInfMavenScanner<Properties>()
    {

        @Override
        protected Properties visitFile( File file )
            throws IOException
        {
            InputStream is = new BufferedInputStream( new FileInputStream( file ) );
            try
            {
                Properties properties = new Properties();
                properties.load( is );
                // TODO validate properties and path match
                return properties;
            }
            finally
            {
                IOUtil.close( is );
            }
        }

        @Override
        protected Properties visitJarEntry( JarFile jar, JarEntry entry )
            throws IOException
        {
            InputStream is = jar.getInputStream( entry );
            try
            {
                Properties properties = new Properties();
                properties.load( is );
                // TODO validate properties and path match
                return properties;
            }
            finally
            {
                IOUtil.close( is );
            }
        }
    };

    public List<T> scan( File location )
        throws CoreException
    {
        Set<T> result = new LinkedHashSet<T>();
        for ( Properties pomProperties : SCANNER.scan( location, "pom.properties" ) )
        {
            T t;

            String projectName = pomProperties.getProperty( "m2e.projectName" );
            File projectLocation = getFile( pomProperties, "m2e.projectLocation" );
            IProject project =
                projectName != null ? ResourcesPlugin.getWorkspace().getRoot().getProject( projectName ) : null;
            if ( project != null && project.isAccessible() && project.getLocation().toFile().equals( projectLocation ) )
            {
                IMavenProjectFacade mavenProject = projectRegistry.getProject( project );

                if ( mavenProject != null )
                {
                    t = visitMavenProject( mavenProject );
                }
                else
                {
                    t = visitProject( project );
                }
            }
            else
            {
                String groupId = pomProperties.getProperty( "groupId" );
                String artifactId = pomProperties.getProperty( "artifactId" );
                String version = pomProperties.getProperty( "version" );
                t = visitGAVC( groupId, artifactId, version, null );
            }

            if ( t != null )
            {
                result.add( t );
            }
        }

        if ( location.isFile() )
        {
            IndexedArtifactFile indexed = identify( location );
            if ( indexed != null )
            {
                ArtifactKey a = indexed.getArtifactKey();
                T t = visitGAVC( a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier() );
                if ( t != null )
                {
                    result.add( t );
                }
            }
        }

        return new ArrayList<T>( result );
    }

    protected IndexedArtifactFile identify( File file )
        throws CoreException
    {
        IIndex index = MavenPlugin.getIndexManager().getAllIndexes();

        List<IndexedArtifactFile> identified;
        if ( index instanceof CompositeIndex )
        {
            identified = ( (CompositeIndex) index ).identifyAll( file );
        }
        else
        {
            IndexedArtifactFile indexed = index.identify( file );
            if ( indexed != null )
            {
                identified = Collections.singletonList( indexed );
            }
            else
            {
                identified = Collections.emptyList();
            }
        }

        for ( IndexedArtifactFile indexed : identified )
        {
            if ( indexed.sourcesExists == IIndex.PRESENT )
            {
                return indexed;
            }
        }

        return null;
    }

    protected T visitGAVC( String groupId, String artifactId, String version, String classifier )
        throws CoreException
    {
        T t;
        IMavenProjectFacade mavenProject = projectRegistry.getMavenProject( groupId, artifactId, version );
        if ( mavenProject != null )
        {
            t = visitMavenProject( mavenProject );
        }
        else
        {
            t = visitArtifact( new ArtifactKey( groupId, artifactId, version, classifier ) );
        }
        return t;
    }

    private File getFile( Properties properties, String name )
    {
        String value = properties.getProperty( name );
        return value != null ? new File( value ) : null;
    }

    protected abstract T visitArtifact( ArtifactKey artifact )
        throws CoreException;

    protected abstract T visitMavenProject( IMavenProjectFacade mavenProject );

    protected abstract T visitProject( IProject project );

}
