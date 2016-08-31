/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.commandline.dbms;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;

import org.neo4j.commandline.admin.AdminCommand;
import org.neo4j.commandline.admin.CommandFailed;
import org.neo4j.commandline.admin.IncorrectUsage;
import org.neo4j.commandline.admin.OutsideWorld;
import org.neo4j.dbms.DatabaseManagementSystemSettings;
import org.neo4j.dbms.archive.Dumper;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.Args;
import org.neo4j.server.configuration.ConfigLoader;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import static org.neo4j.dbms.DatabaseManagementSystemSettings.database_path;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.kernel.impl.util.Converters.identity;
import static org.neo4j.kernel.impl.util.Converters.mandatory;

public class DumpCommand implements AdminCommand
{
    public static class Provider extends AdminCommand.Provider
    {
        public Provider()
        {
            super( "dump" );
        }

        @Override
        public Optional<String> arguments()
        {
            return Optional.of( "" );
        }

        @Override
        public String description()
        {
            return "";
        }

        @Override
        public AdminCommand create( Path homeDir, Path configDir, OutsideWorld outsideWorld )
        {
            return new DumpCommand( homeDir, configDir, new Dumper() );
        }
    }

    private final Path homeDir;
    private final Path configDir;
    private final Dumper dumper;

    public DumpCommand( Path homeDir, Path configDir, Dumper dumper )
    {
        this.homeDir = homeDir;
        this.configDir = configDir;
        this.dumper = dumper;
    }

    @Override
    public void execute( String[] args ) throws IncorrectUsage, CommandFailed
    {
        String database = parse( args, "database", identity() );
        Path archive = calcuateArchive( database, parse( args, "to", Paths::get ) );
        dump( database, toDatabaseDirectory( database ), archive );
    }

    private <T> T parse( String[] args, String argument, Function<String, T> converter ) throws IncorrectUsage
    {
        try
        {
            return Args.parse( args ).interpretOption( argument, mandatory(), converter );
        }
        catch ( IllegalArgumentException e )
        {
            throw new IncorrectUsage( e.getMessage() );
        }
    }

    private Path toDatabaseDirectory( String databaseName )
    {
        //noinspection unchecked
        return new ConfigLoader( asList( DatabaseManagementSystemSettings.class, GraphDatabaseSettings.class ) )
                .loadConfig(
                        Optional.of( homeDir.toFile() ),
                        Optional.of( configDir.resolve( "neo4j.conf" ).toFile() ) )
                .with( stringMap( DatabaseManagementSystemSettings.active_database.name(), databaseName ) )
                .get( database_path ).toPath();
    }

    private Path calcuateArchive( String database, Path to )
    {
        Path archive;
        if ( Files.exists( to ) && Files.isDirectory( to ) )
        {
            archive = to.resolve( database + ".dump" );
        }
        else
        {
            archive = to;
        }
        return archive;
    }

    private void dump( String database, Path databaseDirectory, Path archive ) throws CommandFailed
    {
        try
        {
            dumper.dump( databaseDirectory, archive );
        }
        catch ( FileAlreadyExistsException e )
        {
            throw new CommandFailed( "archive already exists: " + e.getMessage(), e );
        }
        catch ( NoSuchFileException e )
        {
            if ( Paths.get( e.getMessage() ).toAbsolutePath().equals( databaseDirectory.toAbsolutePath() ) )
            {
                throw new CommandFailed( "database does not exist: " + database, e );
            }
            wrapIOException( e );
        }
        catch ( IOException e )
        {
            wrapIOException( e );
        }
    }

    private void wrapIOException( IOException e ) throws CommandFailed
    {
        throw new CommandFailed(
                format( "unable to dump database: %s: %s", e.getClass().getSimpleName(), e.getMessage() ), e );
    }
}
