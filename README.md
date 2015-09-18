# GoCD DataDog Notifier Plugin #

[![Apache 2.0 License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

Copyright 2014 PagerDuty, Inc.

The GoCD PagerDuty Notifier will create an incident when specified pipelines fail.  

## Setup ##

Place plugin jar in GoCD external plugin directory, set up configuration file, and restart Go server. 

## Configuration ##

The configuration file (named pagerduty-notify.conf) must be set up and placed in the Go home directory (probably /var/go).
It uses the [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) format.

Example file contents:

    pagerduty {
      # PagerDuty API key
      api_key =
      # Names of pipelines to alert on if there is a failure
      pipelines = []
      # Statuses to alert on
      statuses_to_alert = [Failed]
    }

## License ##

http://www.apache.org/licenses/LICENSE-2.0

This plugin was based on the GoCD Slack Notification Plugin
https://github.com/ashwanthkumar/gocd-slack-build-notifier