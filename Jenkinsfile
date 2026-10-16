// Enterprise Automation Framework - orchestration pipeline.
// Requires: JDK 21 tool named 'jdk21' (Manage Jenkins > Tools), plugins: Pipeline, JUnit, HTML Publisher (optional).
// Results are written to the quality-metrics DB (RESULTS_DB_URL) and appear on the dashboard automatically.
pipeline {
    agent any

    parameters {
        choice(name: 'SUITE', choices: ['all', 'api', 'web'], description: 'Which automation project(s) to run')
        string(name: 'TAGS', defaultValue: '', description: 'Cucumber tag expression, e.g. "@sanity" or "@regression and not @wip" (empty = everything)')
        choice(name: 'TEST_ENV', choices: ['local', 'staging', 'production'], description: 'Loads config/<env>.properties')
        choice(name: 'WEB_ENGINE', choices: ['playwright', 'selenium'], description: 'Browser automation engine for web suites')
        choice(name: 'RUN_MODE', choices: ['headless', 'chrome'], description: 'Web suites: headless Chromium, or a visible Google Chrome window (agent needs Chrome and a desktop session)')
        string(name: 'RESULTS_DB_URL', defaultValue: '', description: 'JDBC URL of the dashboard DB (empty = <workspace>/results/automation-results.db)')
        booleanParam(name: 'TESTLINK', defaultValue: false, description: 'Also push results to TestLink (needs TESTLINK_DEVKEY credential)')
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
    if (params.TESTLINK) {
        args += " -Dtestlink.enabled=true -Dtestlink.build=jenkins-${env.BUILD_NUMBER}"
    }
    return args
}

def mvn(String args) {
    def run = { -> isUnix() ? sh("./mvnw ${args}") : bat("mvnw.cmd ${args}") }
    if (params.TESTLINK) {
        withCredentials([string(credentialsId: 'TESTLINK_DEVKEY', variable: 'TESTLINK_DEVKEY')]) { run() }
    } else {
        run()
    }
}
