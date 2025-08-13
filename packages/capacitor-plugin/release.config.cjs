module.exports = {
  branches: [
    { name: 'main', channel: 'latest' },
    { name: 'next', channel: 'next', prerelease: true },
    { name: 'development', channel: 'dev', prerelease: true }
  ],
  repositoryUrl: 'https://github.com/ionic-team/capacitor-geolocation.git',
  plugins: [
    '@semantic-release/commit-analyzer',
    '@semantic-release/release-notes-generator',
    [
      "@semantic-release/changelog",
      {
        "changelogFile": "../../CHANGELOG.md"
      }
    ],
    '@semantic-release/npm',
    [
      '@semantic-release/github',
      {
        successComment: false,
        failComment: false,
        releasedLabels: false,
        addReleases: 'bottom',
        releaseNotes: {
          changelogFile: '../../CHANGELOG.md'
        }
      }
    ],
    [
      "@semantic-release/exec",
      {
        // This is necessary because @semantic-release/git won't commit files in parent directory
        //  see  https://github.com/semantic-release/git/issues/485
        prepareCmd:
          "git add ../../CHANGELOG.md",
      },
    ],
    [
      '@semantic-release/git',
      {
        assets: ['package.json'],
        message: 'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}'
      }
    ]
  ]
};
