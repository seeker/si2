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

  group "admin" {
    count = 1

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

    task "rabbitmq" {
      driver = "docker"
      config {
        image = "rabbitmq:3.8.3"

        volumes = ["local/enabled_plugins:/etc/rabbitmq/enabled_plugins"]

        port_map {
          amqp = 5672
          amqps = 5671
        }
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 1024

        network {
          mbits = 900
          
          port "http" {
            static = 15672
          }

          port "https" {
            static = 15671
          }

          port "amqp" {
            static = 5672
          }

          port "amqps" {}
        }
      }

      service {
        name = "rabbitmq-management"
        tags = ["message-broker", "http"]
        port = "http"

        check {
          name     = "HTTP check"
          type     = "http"
          port     = "http"
          path     = "/"
          interval = "10s"
          timeout  = "2s"
        }
      }

      service {
        name = "rabbitmq"
        tags = ["message-broker", "amqp"]
        port = "amqp"

        check {
          name     = "AMQP check"
          type     = "tcp"
          port     = "amqp"
          interval = "10s"
          timeout  = "2s"
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
                EOH
        
        destination = "${NOMAD_SECRETS_DIR}/env"
        env = true
      }
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
          memory = 512

          network {
            mbits = 100
            
            port "db" {
              static = 27017
            }
          }
        }

        service {
          name = "mongodb"
          tags = ["mongodb", "database"]
          port = "db"

          check {
            name     = "Mongodb check"
            type     = "tcp"
            port     = "db"
            interval = "10s"
            timeout  = "2s"
          }
        }
      }
  }
}
