# Changelog

All notable changes to the Android app are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are the Git tags `vX.Y.Z`.

## [Unreleased]

### Added

- M1, sign in, accounts, transactions: email sign-in with the two-factor step and trusted devices, registration,
  email verification, password reset and account restore through App Links; the session with silent refresh;
  the app lock with a PIN and biometrics; the overview with the balance figure, this month and the recent
  transactions; the accounts with groups, debts and the Monobank difference; the transaction feed with periods,
  filters, search, paging and transfer-pair hints; the editor with splits, tags, debts and arithmetic in the amount;
  settings for the profile, security (password, two-factor), the lock, privacy and about; Ukrainian and English,
  light and dark.
- M0, project skeleton: Gradle build with a version catalog and convention plugins, the module layout, the
  design system (theme, typography, palette, icons) with a debug gallery, the API client generated from the
  backend's OpenAPI contract, and CI with lint, detekt, unit tests, assembly and an API drift check.
