#!/bin/bash
find . -type d -name "bin" -exec rm -rf {} +
find . -type d -name "build" -exec rm -rf {} +
find . -type d -name ".settings" -exec rm -rf {} +
find . -type f -name ".project" -delete
find . -type f -name ".classpath" -delete