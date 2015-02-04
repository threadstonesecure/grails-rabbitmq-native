package com.budjb.rabbitmq.test

import org.codehaus.groovy.grails.commons.GrailsApplication

import spock.lang.Specification

import com.budjb.rabbitmq.*
import com.budjb.rabbitmq.converter.*

import com.rabbitmq.client.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Envelope


class RabbitConsumerAdapterSpec extends Specification {
    /**
     * Message converter manager.
     */
    MessageConverterManager messageConverterManager

    /**
     * Sets up the mocked environment for each test.
     */
    def setup() {
        messageConverterManager = new MessageConverterManager()
        messageConverterManager.registerMessageConverter(new IntegerMessageConverter())
        messageConverterManager.registerMessageConverter(new MapMessageConverter())
        messageConverterManager.registerMessageConverter(new ListMessageConverter())
        messageConverterManager.registerMessageConverter(new GStringMessageConverter())
        messageConverterManager.registerMessageConverter(new StringMessageConverter())
    }

    /**
     * Test that the adapter accurately handles configurations defined locally inside a consumer.
     */
    void 'Validate the parsed consumer configuration for a locally defined configuration'() {
        setup:
        // Mock the grails application bean and a blank config
        GrailsApplication grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> new ConfigObject()

        //  Mock the rabbit context
        RabbitContext rabbitContext = Mock(RabbitContext)

        when:
        // Create the adapter
        RabbitConsumerAdapter adapter = new RabbitConsumerAdapter.RabbitConsumerAdapterBuilder().build {
            delegate.consumer = new LocalConfigConsumer()
            delegate.grailsApplication = grailsApplication
            delegate.rabbitContext = rabbitContext
            delegate.messageConverterManager= messageConverterManager
        }

        // Get the configuration
        ConsumerConfiguration configuration = adapter.configuration

        then:
        // Validate the consumer
        adapter.getConsumerName() == 'LocalConfigConsumer'

        // Validate the configuration options
        configuration.queue == 'local-config-queue'
        configuration.consumers == 5
        configuration.retry == false

        // Validate that the consumer is valid
        adapter.isConsumerValid() == true
    }

    /**
     * Test that the adapter accurately handles configurations defined centrally in the application configuration.
     */
    void 'Validate the parsed consumer configuration for a centrally defined configuration'() {
        setup:
        // Mock the grails applicaton config
        GrailsApplication grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> new ConfigObject([
            'rabbitmq': [
                'consumers': [
                    'CentralConfigConsumer': [
                        'queue': 'central-config-queue',
                        'consumers': 10,
                        'retry': true
                    ]
                ]
            ]
        ])

        //  Mock a rabbit context
        RabbitContext rabbitContext = Mock(RabbitContext)

        when:
        // Create the adapter
        RabbitConsumerAdapter adapter = new RabbitConsumerAdapter.RabbitConsumerAdapterBuilder().build {
            delegate.consumer = new CentralConfigConsumer()
            delegate.grailsApplication = grailsApplication
            delegate.rabbitContext = rabbitContext
            delegate.messageConverterManager= messageConverterManager
        }

        // Get the configuration
        ConsumerConfiguration configuration = adapter.configuration

        then:
        // Validate the consumer name
        adapter.getConsumerName() == 'CentralConfigConsumer'

        // Validate the configuration options
        configuration.queue == 'central-config-queue'
        configuration.consumers == 10
        configuration.retry == true

        // Validate that the consumer is valid
        adapter.isConsumerValid() == true
    }

    /**
     * Test most of the callbacks.
     */
    void 'Verify that the proper consumer callbacks are invoked for a successful message'() {
        setup:
        // Mock the grails applicaton config
        GrailsApplication grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> new ConfigObject([
            'rabbitmq': [
                'consumers': [
                    'CallbackConsumer': [
                        'queue': 'callback-queue'
                    ]
                ]
            ]
        ])

        //  Mock a rabbit context
        RabbitContext rabbitContext = Mock(RabbitContext)

        // Create a mocked consumer
        CallbackConsumer consumer = Mock(CallbackConsumer)

        // Create the adapter
        RabbitConsumerAdapter adapter = Spy(RabbitConsumerAdapter, constructorArgs: [
            consumer, grailsApplication, rabbitContext, messageConverterManager, null
        ])

        // Mock the consumer name (sigh)
        adapter.getConsumerName() >> 'CallbackConsumer'

        // Mock a message context
        MessageContext context = new MessageContext(
            channel: Mock(Channel),
            consumerTag: '',
            envelope: Mock(Envelope),
            properties: Mock(BasicProperties),
            body: 'test body'.getBytes(),
            connectionContext: Mock(ConnectionContext)
        )

        when:
        // Hand off the message to the adapter
        adapter.deliverMessage(context)

        then:
        // Ensure that the callbacks were called
        1 * consumer.onReceive(context)
        1 * consumer.onSuccess(context)
        1 * consumer.onComplete(context)
        0 * consumer.onFailure(context)
    }

    /**
     * Test most of the callbacks.
     */
    void 'Verify that the proper consumer callbacks are invoked for an unsuccessful message'() {
        setup:
        // Mock the grails applicaton config
        GrailsApplication grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> new ConfigObject([
            'rabbitmq': [
                'consumers': [
                    'CallbackConsumer': [
                        'queue': 'callback-queue'
                    ]
                ]
            ]
        ])

        //  Mock a rabbit context
        RabbitContext rabbitContext = Mock(RabbitContext)

        // Create a mocked consumer
        CallbackConsumer consumer = Mock(CallbackConsumer)

        // Force an exception when the handler is called
        consumer.handleMessage(*_) >> { throw new RuntimeException() }

        // Create the adapter
        RabbitConsumerAdapter adapter = Spy(RabbitConsumerAdapter, constructorArgs: [
            consumer, grailsApplication, rabbitContext, messageConverterManager, null
        ])

        // Mock the consumer name (sigh)
        adapter.getConsumerName() >> 'CallbackConsumer'

        // Mock a message context
        MessageContext context = new MessageContext(
            channel: Mock(Channel),
            consumerTag: '',
            envelope: Mock(Envelope),
            properties: Mock(BasicProperties),
            body: 'test body'.getBytes(),
            connectionContext: Mock(ConnectionContext)
        )

        when:
        // Hand off the message to the adapter
        adapter.deliverMessage(context)

        then:
        // Ensure that the callbacks were called
        1 * consumer.onReceive(context)
        0 * consumer.onSuccess(context)
        1 * consumer.onComplete(context)
        1 * consumer.onFailure(context)
    }

    void 'Start a basic consumer'() {
        setup:
        // Mock the grails applicaton config
        GrailsApplication grailsApplication = Mock(GrailsApplication)
        grailsApplication.getConfig() >> new ConfigObject()

        // Mock a connection context
        ConnectionContext context = Mock(ConnectionContext)
        context.getName() >> 'default'
        context.createChannel(*_) >> {
            Channel channel = Mock(Channel)
            return channel
        }

        //  Mock a rabbit context that returns the mocked connection context
        RabbitContext rabbitContext = Mock(RabbitContext)
        rabbitContext.getConnection(*_) >> context

        // Create a consumer
        LocalConfigConsumer consumer = new LocalConfigConsumer()

        when:
        // Create the adapter
        RabbitConsumerAdapter adapter = new RabbitConsumerAdapter.RabbitConsumerAdapterBuilder().build {
            delegate.consumer = consumer
            delegate.grailsApplication = grailsApplication
            delegate.rabbitContext = rabbitContext
            delegate.messageConverterManager= messageConverterManager
        }

        // Start the adapter
        adapter.start()

        then:
        adapter.consumers.size() == 5
    }

    /**
     * Used to test a consumer with a local configuration.
     */
    class LocalConfigConsumer {
        static rabbitConfig = [
            'queue': 'local-config-queue',
            'consumers': 5,
            'retry': false
        ]

        def handleMessage(def body, def context) {

        }
    }

    /**
     * Used to test a consumer with a central configuration.
     */
    class CentralConfigConsumer {
        def handleMessage(def body, def context) {

        }
    }

    /**
     * Used to test callbacks.
     */
    class CallbackConsumer {
        def handleMessage(def body, def context) {

        }

        void onReceive(def context) {

        }

        def onSuccess(def context) {

        }

        def onComplete(def context) {

        }

        def onFailure(def context) {

        }
    }
}
