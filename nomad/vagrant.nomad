# For more information and examples on the "job" stanza, please see
# the online documentation at:
#
#     https://www.nomadproject.io/docs/job-specification/job.html
#
job "si2" {
  datacenters = ["vagrant"]

  update {
    max_parallel = 1
    min_healthy_time = "10s"
    healthy_deadline = "3m"
    progress_deadline = "10m"
    auto_revert = false
    canary = 0
  }

  migrate {
    max_parallel = 1
    health_check = "checks"
    min_healthy_time = "10s"
    healthy_deadline = "5m"
  }
  
  group "database" {
    count = 1
    network {
      mode = "bridge"
      port "db" {
        static = 27017
        to = 27017
      }
    }

    service {
      connect {
        sidecar_service {}
      }

      name = "mongodb"
      tags = ["mongodb", "database"]
      port = "27017"
    }

    volume "mongodb" {
      type      = "host"
      source    = "mongodb"
    }

    restart {
      attempts = 2
      interval = "30m"

      delay = "15s"
      mode = "fail"
    }

    ephemeral_disk {
      size = 300
    }

    task "mongodb" {
        driver = "docker"
        config {
          image = "mongo:4.2.6"
        }

        volume_mount {
          volume      = "mongodb"
          destination = "/data/db"
        }

        resources {
          cpu    = 500 # 500 MHz
          memory = 256

          network {
            mbits = 100
          }
        }
      }
  }

  group "rabbitmq" {
    count = 1
    network {
      mode = "bridge"
      port "http" {
        static = 15672
        to = 15672
      }

      port "https" {
        static = 15671
        to = 15671
      }

      port "amqp" {
        static = 5672
        to = 5672
      }
    }

    service {
      connect {
        sidecar_service {}
      }
      name = "rabbitmq"
      port = "5672"
    }

    restart {
      attempts = 2
      interval = "30m"

      delay = "15s"
      mode = "fail"
    }

    ephemeral_disk {
      size = 300
    }

    task "rabbitmq" {
      driver = "docker"
      config {
        image = "rabbitmq:3.8.3"

        volumes = ["local/enabled_plugins:/etc/rabbitmq/enabled_plugins"]
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 768

        network {
          mbits = 500
        }
      }

      vault {
        policies = ["si2-rabbitmq-nomad"]

        change_mode   = "signal"
        change_signal = "SIGHUP"
      }

      template {
        data = <<EOH
                [rabbitmq_management,rabbitmq_shovel,rabbitmq_shovel_management].
                EOH
        destination = "${NOMAD_TASK_DIR}/enabled_plugins"
      }

      template {
        data = <<EOH
                  {{ with secret "kv/rabbitmq/admin" }}
                    RABBITMQ_DEFAULT_USER = "{{ .Data.username }}"
                    RABBITMQ_DEFAULT_PASS = {{ .Data.password | toJSON }}
                  {{ end }}
                    RABBITMQ_DEFAULT_VHOST = "{{key "config/rabbitmq/vhost"}}"
                EOH
        
        destination = "${NOMAD_SECRETS_DIR}/env"
        env = true
      }
    }
  }

  group "registry" {
    count = 1
    network {
      mode = "bridge"
      port "docker_registry" {
        static = 5000
        to = 5000
      }
    }

    service {
      connect {
        sidecar_service {}
      }

      name = "docker-registry"
      tags = ["docker", "registry"]
      port = "5000"
    }

    restart {
      attempts = 2
      interval = "30m"

      delay = "15s"
      mode = "fail"
    }

    ephemeral_disk {
      size = 300
    }

    task "registry" {
      driver = "docker"
      config {
        image = "registry:2.7.1"

        mounts =
          [
            {
              type = "bind"
              target = "/var/lib/registry"
              source = "/var/lib/docker-registry"
              readonly = false
            }
          ]
      }

      vault {
        policies = ["si2-registry-nomad"]

        change_mode   = "signal"
        change_signal = "SIGHUP"
      }

      template {
        data = <<EOH
      {{ with secret "pki/issue/registry" "common_name=docker-registry.service.consul" "ip_sans=127.0.0.1" }}
      {{- .Data.certificate -}}
      {{ end }}
      EOH
        destination   = "${NOMAD_TASK_DIR}/certificate.crt"
        change_mode   = "restart"
      }

      template {
        data = <<EOH
      {{ with secret "pki/issue/registry" "common_name=docker-registry.service.consul" "ip_sans=127.0.0.1" }}
      {{- .Data.private_key -}}
      {{ end }}
      EOH
        destination   = "${NOMAD_TASK_DIR}/private_key.key"
        change_mode   = "restart"
      }

      env {
        REGISTRY_HTTP_ADDR            = "0.0.0.0:5000"
        #REGISTRY_HTTP_TLS_CERTIFICATE = "/certs/certificate.crt"
        #REGISTRY_HTTP_TLS_KEY         = "/certs/private_key.key"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 256

        network {
          mbits = 100
        }
      }
    }
  }
}
