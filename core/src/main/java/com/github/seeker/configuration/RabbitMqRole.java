/* The MIT License (MIT)
 * Copyright (c) 2019 Nicholas Wright
 * http://opensource.org/licenses/MIT
 */
package com.github.seeker.configuration;

public enum RabbitMqRole {
	hash_processor,
	dbnode,
	file_loader,
	image_resizer,
	digest_hasher,
	thumbnail,
	integration,
	client
}
