/*
 * Copyright 2007-2014 the original author or authors
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.integration.redis.inbound;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.support.converter.SimpleMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.1
 */
public class RedisInboundChannelAdapter extends MessageProducerSupport {

	private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();

	private volatile MessageConverter messageConverter = new SimpleMessageConverter();

	private volatile String[] topics;

	private volatile String[] topicPatterns;

	private volatile RedisSerializer<?> serializer = new StringRedisSerializer();

	public RedisInboundChannelAdapter(RedisConnectionFactory connectionFactory) {
		Assert.notNull(connectionFactory, "connectionFactory must not be null");
		this.container.setConnectionFactory(connectionFactory);
	}

	public void setSerializer(RedisSerializer<?> serializer) {
		this.serializer = serializer;
	}

	public void setTopics(String... topics) {
		this.topics = topics;
	}

	public void setTopicPatterns(String... topicPatterns) {
		this.topicPatterns = topicPatterns;
	}

	public void setMessageConverter(MessageConverter messageConverter) {
		Assert.notNull(messageConverter, "messageConverter must not be null");
		this.messageConverter = messageConverter;
	}

	@Override
	public String getComponentType() {
		return "redis:inbound-channel-adapter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		boolean hasTopics = false;
		if (this.topics != null) {
			Assert.noNullElements(this.topics, "'topics' may not contain null elements.");
			hasTopics = true;
		}
		boolean hasPatterns = false;
		if (this.topicPatterns != null) {
			Assert.noNullElements(this.topicPatterns, "'topicPatterns' may not contain null elements.");
			hasPatterns = true;

		}
		Assert.state(hasTopics || hasPatterns, "at least one topic or topic pattern is required for subscription.");

		if (this.messageConverter instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.messageConverter).setBeanFactory(this.getBeanFactory());
		}
		MessageListenerDelegate delegate = new MessageListenerDelegate();
		MessageListenerAdapter adapter = new MessageListenerAdapter(delegate);
		adapter.setSerializer(this.serializer);
		List<Topic> topicList = new ArrayList<Topic>();
		if (hasTopics) {
			for (String topic : this.topics) {
				topicList.add(new ChannelTopic(topic));
			}
		}
		if (hasPatterns) {
			for (String pattern : this.topicPatterns) {
				topicList.add(new PatternTopic(pattern));
			}
		}
		adapter.afterPropertiesSet();
		this.container.addMessageListener(adapter, topicList);
		this.container.afterPropertiesSet();
	}

	@Override
	protected void doStart() {
		super.doStart();
		this.container.start();
	}


	@Override
	protected void doStop() {
		super.doStop();
		this.container.stop();
	}

	private Message<?> convertMessage(Object object) {
		return this.messageConverter.toMessage(object, null);
	}


	private class MessageListenerDelegate {

		@SuppressWarnings("unused")
		public void handleMessage(Object object) {
			sendMessage(convertMessage(object));
		}
	}

}
