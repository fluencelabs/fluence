/*
 * Copyright 2020 Fluence Labs Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#![recursion_limit = "512"]
#![warn(rust_2018_idioms)]
#![deny(
    dead_code,
    nonstandard_style,
    unused_imports,
    unused_mut,
    unused_variables,
    unused_unsafe,
    unreachable_patterns
)]

mod app_services;
mod bootstrap_config;
mod defaults;
mod fluence_config;
mod kademlia_config;
mod keys;
mod listen_config;
mod network_config;
mod services_config;

pub use defaults::default_air_interpreter_path;
pub use fluence_config::{deserialize_config, load_config};

pub use app_services::AppServicesConfig;
pub use bootstrap_config::BootstrapConfig;
pub use fluence_config::FluenceConfig;
pub use fluence_config::NodeConfig;
pub use kademlia_config::KademliaConfig;
pub use listen_config::ListenConfig;
pub use network_config::NetworkConfig;
pub use services_config::ServicesConfig;

pub mod config_keys {
    pub use crate::fluence_config::{
        BLUEPRINT_DIR, BOOTSTRAP_NODE, CERTIFICATE_DIR, CONFIG_FILE, EXTERNAL_ADDR,
        MANAGEMENT_PEER_ID, ROOT_KEY_PAIR, SERVICES_WORKDIR, SERVICE_ENVS, TCP_PORT,
        WEBSOCKET_PORT,
    };
}
