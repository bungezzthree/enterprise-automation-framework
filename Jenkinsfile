// Enterprise Automation Framework - orchestration pipeline.
// Requires: JDK 21 tool named 'jdk21' (Manage Jenkins > Tools), plugins: Pipeline, JUnit, HTML Publisher (optional).
// Results are written to the quality-metrics DB (RESULTS_DB_URL) and appear on the dashboard automatically.
pipeline {
    agent any

    parameters {
        choice(name: 'SUITE', choices: ['all', 'api', 'web', 'android'], description: 'Which automation project(s) to run')
        string(name: 'TAGS', defaultValue: '', description: 'Cucumber tag expression, e.g. "@sanity" or "@regression and not @wip" (empty = everything)')
        choice(name: 'TEST_ENV', choices: ['local', 'staging', 'production'], description: 'Loads config/<env>.properties')
        choice(name: 'WEB_ENGINE', choices: ['playwright', 'selenium'], description: 'Browser automation engine for web suites')
        choice(name: 'RUN_MODE', choices: ['headless', 'chrome'], description: 'Web suites: headless Chromium, or a visible Google Chrome window (agent needs Chrome and a desktop session)')
        choice(name: 'ANDROID_MODE', choices: ['headless', 'emulator', 'device'], description: 'Android suite: emulator without a window (CI default), with a window, or a physical device on the agent (needs the Android SDK, Appium and an AVD on the agent: docs/android-setup.md)')
        string(name: 'ANDROID_APP', defaultValue: '', description: 'Path or URL of the .apk to test (empty = the one in android-tests config)')
        string(name: 'RESULTS_DB_URL', defaultValue: '', description: 'JDBC URL of the dashboard DB (empty = <workspace>/results/automation-results.db)')
        booleanParam(name: 'TESTDESK', defaultValue: false, description: 'Also send results and evidence to TestDesk (needs the TESTDESK_TOKEN Secret text credential and TESTDESK_URL in the Jenkins/agent environment)')
        choice(name: 'TESTDESK_ENV', choices: ['SIT', 'UAT'], description: 'TestDesk environment of the test plan (used when the plan is created)')
        string(name: 'TESTDESK_PLAN', defaultValue: '', description: 'TestDesk test plan name, e.g. "Release 2.4" (empty = rolling plan "EAF <project> <env>")')
    }

    triggers {
        // Nightly full regression; sanity is usually triggered by the application's deploy job.
        cron('H 2 * * 1-5')
    }

    tools {
        jdk 'jdk21'
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {
        stage('Build framework') {
            steps {
                script { mvn('-B -q install -DskipTests') }
            }
        }

        stage('Run suites') {
            parallel {
                stage('API') {
                    when { expression { params.SUITE in ['all', 'api'] } }
                    steps {
                        script { mvn("-B -pl api-tests test ${commonArgs()}") }
                    }
                }
                stage('Web') {
                    when { expression { params.SUITE in ['all', 'web'] } }
                    steps {
                        script {
                            mvn("-B -pl web-tests test ${commonArgs()} -Dweb.engine=${params.WEB_ENGINE} -P${params.RUN_MODE}")
                        }
                    }
                }
            }
        }

        // Android needs the machine's CPU for the emulator, so it does not run alongside the Web browsers.
        stage('Android') {
            when { expression { params.SUITE in ['all', 'android'] } }
            steps {
                script {
                    def app = params.ANDROID_APP?.trim() ? " \"-Dmobile.app=${params.ANDROID_APP.trim()}\"" : ''
                    mvn("-B -pl android-tests test ${commonArgs()} -P${params.ANDROID_MODE}${app}")
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/target/cucumber-reports/cucumber.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/cucumber-reports/**'
        }
    }
}

def commonArgs() {
    def args = "-Denv=${params.TEST_ENV} -Dmaven.test.failure.ignore=true"
    if (params.TAGS?.trim()) {
        args += " \"-Dcucumber.filter.tags=${params.TAGS.trim()}\""
    }
    if (params.RESULTS_DB_URL?.trim()) {
        args += " \"-Dresults.db.url=${params.RESULTS_DB_URL.trim()}\""
    }
    if (params.TESTDESK) {
        args += " -Dtestdesk.enabled=true -Dtestdesk.environment=${params.TESTDESK_ENV}"
        if (params.TESTDESK_PLAN?.trim()) {
            args += " \"-Dtestdesk.plan.name=${params.TESTDESK_PLAN.trim()}\""
        }
    }
    return args
}

def mvn(String args) {
    def run = { -> isUnix() ? sh("./mvnw ${args}") : bat("mvnw.cmd ${args}") }
    if (params.TESTDESK) {
        // Config reads testdesk.token only from env TESTDESK_TOKEN; testdesk.url comes from env TESTDESK_URL on the agent.
        withCredentials([string(credentialsId: 'TESTDESK_TOKEN', variable: 'TESTDESK_TOKEN')]) { run() }
    } else {
        run()
    }
}
