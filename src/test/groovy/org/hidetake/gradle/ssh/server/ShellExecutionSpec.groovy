package org.hidetake.gradle.ssh.server

import org.apache.sshd.SshServer
import org.apache.sshd.common.Factory
import org.apache.sshd.server.PasswordAuthenticator
import org.hidetake.groovy.ssh.api.session.BadExitStatusException
import org.hidetake.groovy.ssh.internal.operation.DefaultOperations
import org.hidetake.groovy.ssh.server.ServerIntegrationTest
import org.hidetake.groovy.ssh.server.SshServerMock
import org.hidetake.groovy.ssh.server.SshServerMock.CommandContext
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.mop.ConfineMetaClassChanges

import static org.hidetake.groovy.ssh.Ssh.ssh

@org.junit.experimental.categories.Category(ServerIntegrationTest)
class ShellExecutionSpec extends Specification {

    SshServer server

    def setup() {
        server = SshServerMock.setUpLocalhostServer()
        server.passwordAuthenticator = Mock(PasswordAuthenticator) {
            _ * authenticate('someuser', 'somepassword', _) >> true
        }

        ssh.settings {
            knownHosts = allowAnyHosts
        }
        ssh.remotes {
            testServer {
                host = server.host
                port = server.port
                user = 'someuser'
                password = 'somepassword'
            }
        }
    }

    def cleanup() {
        ssh.remotes.clear()
        ssh.proxies.clear()
        ssh.settings.reset()
        server.stop(true)
    }


    def "exit 0"() {
        given:
        def factoryMock = Mock(Factory)
        server.shellFactory = factoryMock
        server.start()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                shell(interaction: {})
            }
        }

        then:
        1 * factoryMock.create() >> SshServerMock.command { CommandContext c ->
            c.exitCallback.onExit(0)
        }
    }

    def "exit 1"() {
        given:
        def factoryMock = Mock(Factory)
        server.shellFactory = factoryMock
        server.start()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                shell(interaction: {})
            }
        }

        then:
        1 * factoryMock.create() >> SshServerMock.command { CommandContext c ->
            c.exitCallback.onExit(1)
        }

        then:
        BadExitStatusException e = thrown()
        e.exitStatus == 1
    }

    @Unroll
    @ConfineMetaClassChanges(DefaultOperations)
    def "logging, #description"() {
        given:
        def logger = Mock(Logger) {
            isInfoEnabled() >> true
        }
        DefaultOperations.metaClass.static.getLog = { -> logger }

        server.shellFactory = Mock(Factory) {
            1 * create() >> SshServerMock.command { CommandContext c ->
                c.outputStream.withWriter('UTF-8') { it << outputValue }
                c.exitCallback.onExit(0)
            }
        }
        server.start()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                shell(interaction: {})
            }
        }

        then:
        logMessages.each { 1 * logger.info(it) }

        where:
        description            | outputValue                  | logMessages
        'a line'               | 'some result'                | ['some result']
        'a line with line sep' | 'some result\n'              | ['some result']
        'lines'                | 'some result\nsecond line'   | ['some result', 'second line']
        'lines with line sep'  | 'some result\nsecond line\n' | ['some result', 'second line']
    }

    @Unroll
    @ConfineMetaClassChanges(DefaultOperations)
    def "toggle logging = #logging"() {
        given:
        def logger = Mock(Logger) {
            isInfoEnabled() >> true
        }
        DefaultOperations.metaClass.static.getLog = { -> logger }

        server.shellFactory = Mock(Factory) {
            1 * create() >> SshServerMock.command { CommandContext c ->
                c.outputStream.withWriter('UTF-8') { it << 'some message' }
                c.exitCallback.onExit(0)
            }
        }
        server.start()

        when:
        ssh.run {
            session(ssh.remotes.testServer) {
                shell(logging: logging)
            }
        }

        then:
        (logging ? 1 : 0) * logger.info('some message')

        where:
        logging << [true, false]
    }

}
