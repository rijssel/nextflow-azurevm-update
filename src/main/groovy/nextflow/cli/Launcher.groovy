/*
 * Copyright (c) 2013-2014, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2014, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.cli
import static nextflow.Const.APP_BUILDNUM
import static nextflow.Const.APP_NAME
import static nextflow.Const.APP_VER
import static nextflow.Const.SEE_LOG_FOR_DETAILS
import static nextflow.Const.SPLASH

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import com.beust.jcommander.Parameters
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.ExitCode
import nextflow.exception.AbortOperationException
import nextflow.exception.ConfigParseException
import nextflow.util.LoggerHelper
import org.eclipse.jgit.api.errors.GitAPIException
/**
 * Main application entry point. It parses the command line and
 * launch the pipeline execution.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class Launcher implements ExitCode {

    /**
     * Create the application command line parser
     *
     * @return An instance of {@code CliBuilder}
     */

    private JCommander jcommander

    private CliOptions options

    private boolean fullVersion

    private CmdBase command

    private String cliString

    private List<CmdBase> allCommands

    private List<String> normalizedArgs

    private boolean daemonMode

    private String colsString

    /**
     * Create a launcher object and parse the command line parameters
     *
     * @param args The command line arguments provided by the user
     */
    Launcher() {
        init()
    }

    protected void init() {
        allCommands = (List<CmdBase>)[
                new CmdClone(),
                new CmdHistory(),
                new CmdInfo(),
                new CmdList(),
                new CmdPull(),
                new CmdRun(),
                new CmdDrop(),
                new CmdConfig(),
                new CmdNode(),
                new CmdView(),
                new CmdHelp()
        ]

        options = new CliOptions()
        jcommander = new JCommander(options)
        allCommands.each { cmd ->
            cmd.launcher = this;
            jcommander.addCommand(cmd.name, cmd)
        }
        jcommander.setProgramName( APP_NAME )
    }

    /**
     * Create the Jcommander 'interpreter' and parse the command line arguments
     */
    @PackageScope
    Launcher parseMainArgs(String... args) {
        this.cliString = System.getenv('NXF_CLI')
        this.colsString = System.getenv('COLUMNS')

        def cols = getColumns()
        if( cols )
            jcommander.setColumnSize(cols)

        normalizedArgs = normalizeArgs(args)
        jcommander.parse( normalizedArgs as String[] )
        fullVersion = '-version' in normalizedArgs
        command = allCommands.find { it.name == jcommander.getParsedCommand()  }
        // whether is running a daemon
        daemonMode = command instanceof CmdNode
        // set the log file name
        if( !options.logFile ) {
            if( isDaemon() )
                options.logFile = '.node-nextflow.log'
            else if( command instanceof CmdRun )
                options.logFile = ".nextflow.log"
        }

        return this
    }

    private short getColumns() {
        if( !colsString ) {
            log.debug 'Bash environment $COLUMNS is not defined -- It looks TTY is not available'
            return 0
        }

        try {
            colsString.toShort()
        }
        catch( Exception e ) {
            log.debug "Oops .. not a valid \$COLUMNS value: $colsString"
            return 0
        }
    }

    CliOptions getOptions() { options }

    List<String> getNormalizedArgs() { normalizedArgs }

    String getCliString() { cliString }

    boolean isDaemon() { daemonMode }

    /**
     * normalize the command line arguments to handle some corner cases
     */
    @PackageScope
    static List<String> normalizeArgs( String ... args ) {

        def normalized = []
        int i=0
        while( true ) {
            if( i==args.size() ) { break }

            def current = args[i++]
            normalized << current

            if( current == '-resume' ) {
                if( i<args.size() && !args[i].startsWith('-') && (args[i]=='last' || args[i] =~~ /[0-9a-f]{8}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{8}/) ) {
                    normalized << args[i++]
                }
                else {
                    normalized << 'last'
                }
            }
            else if( current == '-test' && (i==args.size() || args[i].startsWith('-'))) {
                normalized << '%all'
            }

            else if( current == '-with-drmaa' && (i==args.size() || args[i].startsWith('-'))) {
                normalized << '-'
            }

            else if( current == '-with-trace' && (i==args.size() || args[i].startsWith('-'))) {
                normalized << 'trace.csv'
            }

            else if( current == '-with-docker' && (i==args.size() || args[i].startsWith('-'))) {
                normalized << '-'
            }

            else if( current ==~ /^\-\-[a-zA-Z\d].*/ && !current.contains('=')) {
                current += '='
                current += ( i<args.size() ? args[i++] : 'true' )
                normalized[-1] = current
            }

            else if( current ==~ /^\-process\..+/ && !current.contains('=')) {
                current += '='
                current += ( i<args.size() ? args[i++] : 'true' )
                normalized[-1] = current
            }

            else if( current ==~ /^\-cluster\..+/ && !current.contains('=')) {
                current += '='
                current += ( i<args.size() ? args[i++] : 'true' )
                normalized[-1] = current
            }

            else if( current ==~ /^\-executor\..+/ && !current.contains('=')) {
                current += '='
                current += ( i<args.size() ? args[i++] : 'true' )
                normalized[-1] = current
            }

            else if( current == 'run' && i<args.size() && args[i] == '-' ) {
                i++
                normalized << '-stdin'
            }
        }

        return normalized
    }

    /**
     * Print the usage string for the given command - or -
     * the main program usage string if not command is specified
     *
     * @param command The command for which get help or {@code null}
     * @return The usage string
     */
    void usage(String command = null ) {

        if( command ) {
            def exists = allCommands.find { it.name == command } != null
            if( !exists ) {
                println "Asking help for unknown command: $command"
                return
            }

            jcommander.usage(command)
            return
        }

        def list = new ArrayList<CmdBase>(allCommands).findAll { it.name != CmdNode.NAME }
        println "Usage: nextflow [options] COMMAND [arg...]\n"
        println "Commands: "

        int len = 0
        def all = new TreeMap<String,String>()
        list.each {
            def description = it.getClass().getAnnotation(Parameters)?.commandDescription()
            all[it.name] = description ?: '-'
            if( it.name.size()>len ) len = it.name.size()
        }

        all.each { String name, String desc ->
            print '  '
            print name.padRight(len)
            print '   '
            println desc
        }
        println ''
    }

    protected Launcher command( String[] args ) {
        /*
         * CLI argument parsing
         */
        try {
            parseMainArgs(args)
            LoggerHelper.configureLogger(this)
        }
        catch( ParameterException e ) {
            // print command line parsing errors
            // note: use system.err.println since if an exception is raised
            //       parsing the cli params the logging is not configured
            System.err.println "${e.getMessage()} -- Check the available commands and options and syntax with 'help'"
            System.exit( INVALID_COMMAND_LINE_PARAMETER )

        }
        catch( Throwable e ) {
            e.printStackTrace(System.err)
            System.exit( UNKNOWN_ERROR )
        }
        return this
    }

    /**
     * Launch the pipeline execution
     */
    protected void run() {

        /*
         * Real execution starts here
         */
        try {
            log.debug '$> ' + cliString

            // -- print out the version number, then exit
            if ( options.version ) {
                println getVersion(fullVersion)
                System.exit(OK)
            }

            // -- print out the program help, then exit
            if( options.help || !command || command.help ) {
                def target = command?.name
                command = allCommands.find { it instanceof CmdHelp }
                if( target )
                    (command as CmdHelp).args = [target]
            }

            // launch the command
            command.run()

        }

        catch ( GitAPIException | AbortOperationException e ) {
            System.err.println e.getMessage() ?: e.toString()
            log.debug ("Operation aborted", e.cause ?: e)
            System.exit(COMMAND_RUNTIME_ERROR)
        }

        catch( ConfigParseException e )  {
            log.error("${e.message}\n\n${e.cause?.message?.toString()?.indent('  ')}\n  ${SEE_LOG_FOR_DETAILS}\n", e.cause ?: e)
            System.exit(INVALID_CONFIG)
        }

        catch( Throwable fail ) {
            log.error("${fail.toString()} ${SEE_LOG_FOR_DETAILS}", fail)
            System.exit(UNKNOWN_ERROR)
        }

    }

    /**
     * Hey .. Nextflow starts here!
     *
     * @param args The program options as specified by the user on the CLI
     */
    public static void main(String... args)  {

        def launcher = DripMain.LAUNCHER ?: new Launcher()
        launcher .command(args) .run()
    }


    /**
     * Print the application version number
     * @param full When {@code true} prints full version number including build timestamp
     * @return The version number string
     */
    static String getVersion(boolean full = false) {

        if ( full ) {
            SPLASH
        }
        else {
            "${APP_NAME} version ${APP_VER}.${APP_BUILDNUM}"
        }

    }


}
