#!/usr/bin/env bash

publish_plugin_android () {
    PLUGIN_PATH=$1
    if [ -d "$PLUGIN_PATH" ]; then
        # Android dir path
        ANDROID_PATH=$PLUGIN_PATH/android
        GRADLE_FILE=$ANDROID_PATH/build.gradle

        # Only try to publish if the directory contains a package.json and android package
        if test -f "$PLUGIN_PATH/package.json" && test -d "$ANDROID_PATH" && test -f "$GRADLE_FILE"; then
            PLUGIN_VERSION=$(grep '"version": ' "$PLUGIN_PATH"/package.json | awk '{print $2}' | tr -d '",')
            PLUGIN_NAME=$(grep '"name": ' "$PLUGIN_PATH"/package.json | awk '{print $2}' | tr -d '",')
            PLUGIN_NAME=${PLUGIN_NAME#@capacitor/}
            LOG_OUTPUT=./tmp/$PLUGIN_NAME.txt

            # Get latest plugin info from MavenCentral
            PLUGIN_PUBLISHED_URL="https://repo1.maven.org/maven2/com/capacitorjs/$PLUGIN_NAME/maven-metadata.xml"
            PLUGIN_PUBLISHED_DATA=$(curl -s $PLUGIN_PUBLISHED_URL)
            PLUGIN_PUBLISHED_VERSION="$(perl -ne 'print and last if s/.*<latest>(.*)<\/latest>.*/\1/;' <<< $PLUGIN_PUBLISHED_DATA)"

            if [[ $PLUGIN_VERSION == $PLUGIN_PUBLISHED_VERSION ]]; then
                printf %"s\n\n" "Duplicate: a published plugin $PLUGIN_NAME exists for version $PLUGIN_VERSION, skipping..."
            else
                # Make log dir if doesnt exist
                mkdir -p ./tmp

                printf %"s\n" "Attempting to build and publish plugin $PLUGIN_NAME for version $PLUGIN_VERSION to production..."

                # Export ENV variables used by Gradle for the plugin
                export PLUGIN_NAME
                export PLUGIN_VERSION
                export CAPACITOR_VERSION
                export CAP_PLUGIN_PUBLISH=true
                export PLUGIN_REPO="https://github.com/ionic-team/capacitor-geolocation"
                export PLUGIN_SCM="github.com:ionic-team/capacitor-geolocation"

                # Build and publish
                "$ANDROID_PATH"/gradlew clean build publishReleasePublicationToSonatypeRepository closeAndReleaseSonatypeStagingRepository --no-daemon --max-workers 1 -b "$ANDROID_PATH"/build.gradle -Pandroid.useAndroidX=true > $LOG_OUTPUT 2>&1

                if grep --quiet "BUILD SUCCESSFUL" $LOG_OUTPUT; then
                    printf %"s\n\n" "Success: $PLUGIN_NAME published to MavenCentral."
                else
                    printf %"s\n\n" "Error publishing $PLUGIN_NAME, check $LOG_OUTPUT for more info! Manually review and release from the Central Portal may be necessary https://central.sonatype.com/publishing/deployments/"
                    cat $LOG_OUTPUT
                    exit 1
                fi
            fi
        else
            printf %"s\n\n" "$PLUGIN_PATH does not appear to be a plugin (has no package.json file or Android package), skipping..."
        fi
    fi
}

# Get latest com.capacitorjs:core XML version info
CAPACITOR_PUBLISHED_URL="https://repo1.maven.org/maven2/com/capacitorjs/core/maven-metadata.xml"
CAPACITOR_PUBLISHED_DATA=$(curl -s $CAPACITOR_PUBLISHED_URL)
CAPACITOR_PUBLISHED_VERSION="$(perl -ne 'print and last if s/.*<latest>(.*)<\/latest>.*/\1/;' <<< $CAPACITOR_PUBLISHED_DATA)"

printf %"s\n" "The latest published Android library version of Capacitor Core is $CAPACITOR_PUBLISHED_VERSION in MavenCentral."

# Determine Capacitor Version to use as gradle dependency.
STABLE_PART=$(echo "$CAPACITOR_PUBLISHED_VERSION" | cut -d'-' -f1)
IFS='.' read -r MAJOR MINOR PATCH <<< "$STABLE_PART"
if [[ "$CAPACITOR_PUBLISHED_VERSION" == *"-"* ]]; then
  # prerelease - go one major lower (latest stable major), but also allow next upcoming major
  PREV_MAJOR=$((MAJOR - 1))
  NEXT_MAJOR=$((MAJOR + 1))
  CAPACITOR_VERSION="[$PREV_MAJOR.0,$NEXT_MAJOR.0)"
else
  # stable - current major range
  NEXT_MAJOR=$((MAJOR + 1))
  CAPACITOR_VERSION="[$MAJOR.0,$NEXT_MAJOR.0)"
fi
printf %"s\n" "Publishing plugin with dependency on Capacitor version $CAPACITOR_VERSION"

publish_plugin_android '.'
