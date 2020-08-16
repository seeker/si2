# For more information and examples on the "job" stanza, please see
# the online documentation at:
#
#     https://www.nomadproject.io/docs/job-specification/job.html
#
job "si2-nodes" {
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

  group "nodes" {
    count = 1
    network {
      mode = "bridge"
    }

    service {
      connect {
        sidecar_service {}
      }
    }

    volume "thumbnail" {
      type      = "host"
      source    = "thumbnail"
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

    task "db-node" {
      driver = "docker"
      config {
        image = "docker-registry.service.consul:5000/si2-node"
        args = ["db"]
        force_pull = true
      }

      template {
        data = <<EOH
                consul.ip: "10.0.2.15"
                consul.port: 8500
                consul.datacenter: "vagrant"
                vault.secretId: "dbnode"
                vault.approleId: "dbnode"
                EOH
        destination = "local/si2.yaml"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 512
      }
    }

    task "processor-node" {
      driver = "docker"
      config {
        image = "docker-registry.service.consul:5000/si2-node"
        args = ["processor"]
        force_pull = true
      }

      template {
        data = <<EOH
                consul.ip: "10.0.2.15"
                consul.port: 8500
                consul.datacenter: "vagrant"
                vault.secretId: "dbnode"
                vault.approleId: "dbnode"
                EOH
        destination = "local/si2.yaml"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 768
      }
    }

    task "custom-hash-node" {
      driver = "docker"
      config {
        image = "docker-registry.service.consul:5000/si2-node"
        args = ["custom-hash"]
        force_pull = true
      }

      template {
        data = <<EOH
                consul.ip: "10.0.2.15"
                consul.port: 8500
                consul.datacenter: "vagrant"
                vault.secretId: "dbnode"
                vault.approleId: "dbnode"
                EOH
        destination = "local/si2.yaml"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 768
      }
    }

    task "resizer-node" {
      driver = "docker"
      config {
        image = "docker-registry.service.consul:5000/si2-node"
        args = ["resizer"]
        force_pull = true
      }

      template {
        data = <<EOH
                consul.ip: "10.0.2.15"
                consul.port: 8500
                consul.datacenter: "vagrant"
                vault.secretId: "dbnode"
                vault.approleId: "dbnode"
                EOH
        destination = "local/si2.yaml"
      }

      resources {
        cpu    = 4096
        memory = 768
      }
    }

    task "thumb-node" {
      driver = "docker"
      config {
        image = "docker-registry.service.consul:5000/si2-node"
        args = ["thumb","--thumb-dir","/mnt/thumbs"]
        force_pull = true
      }

      volume_mount {
        volume      = "thumbnail"
        destination = "/mnt/thumbs"
      }

      template {
        data = <<EOH
                consul.ip: "10.0.2.15"
                consul.port: 8500
                consul.datacenter: "vagrant"
                vault.secretId: "dbnode"
                vault.approleId: "dbnode"
                EOH
        destination = "local/si2.yaml"
      }

      resources {
        cpu    = 500 # 500 MHz
        memory = 512
      }
    }
  }
}
