import logging
import posixpath
import requests
import urllib3
import os
import enum
from datetime import datetime, timezone

urllib3.disable_warnings()


class BitbucketAPIClient():

    TARGET_PROJECT_NAMES = ['CDT Infrastructure']
    PROTECTED_BRANCHES = ['master', 'staging', 'main']

    class Constants(enum.Enum):
        API_V1_PROJECTS = 'rest/api/1.0/projects?name={}'
        API_V1_REPOSITORIES = 'rest/api/1.0/projects/{}/repos?start={}'
        API_V1_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/branches?start={}'
        API_V1_COMMITS = 'rest/api/1.0/projects/{}/repos/{}/commits/{}'
        API_V1_COMPARE_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/compare/commits?from={}&to={}'
        API_V1_DELETE_BRANCH = 'rest/branch-utils/1.0/projects/{}/repos/{}/branches'

    def __init__(self, baseurl: str, username: str, password: str):
        logging.info("Initializing BitbucketAPIClient for %s", baseurl)

        self.session = requests.session()
        self.baseurl = baseurl
        self.username = username
        self.password = password

        self.session.auth = (self.username, self.password)
        self.session.headers = {'Content-Type': 'application/json'}
        self.session.verify = False

    def list_project_keys(self) -> list[str]:
        logging.info("Fetching project keys for target names: %s", self.TARGET_PROJECT_NAMES)

        project_keys = []

        for project_name in self.TARGET_PROJECT_NAMES:
            url = posixpath.join(self.baseurl, self.Constants.API_V1_PROJECTS.value.format(project_name))

            logging.debug("Requesting project by name: %s", project_name)
            res = self.session.get(url)

            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch key for project '%s': %s", project_name, err)
                continue

            data = res.json()

            if not data.get('values'):
                logging.warning("No projects found for name '%s'", project_name)
                continue

            if len(data.get('values')) > 1:
                logging.error("Multiple projects found for name '%s'. skipping...", project_name)
                continue

            key = data["values"][0]["key"]
            project_keys.append(key)

        logging.info("Collected project keys: %s", project_keys)
        return project_keys

    def list_repository_slugs(self, project_key: str) -> list[str]:
        logging.info("Fetching repositories for project '%s'", project_key)

        page_number = 0
        repo_slugs = []

        while True:
            url = posixpath.join(self.baseurl, self.Constants.API_V1_REPOSITORIES.value.format(project_key, page_number))
            res = self.session.get(url)
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch repositories for '%s': %s", project_key, err)
                return []

            data = res.json()

            for element in data['values']:
                repo_slugs.append(element['slug'])
            
            if data['isLastPage']:
                break
            
            page_number = data['nextPageStart']

        logging.info("Total repositories for '%s': %d", project_key, len(repo_slugs))
        return repo_slugs

    def list_branches(self, project_key: str, repo_slug: str):
        logging.info("Fetching branches for '%s/%s'", project_key, repo_slug)

        page_number = 0
        branches = {}

        while True:
            url = posixpath.join(self.baseurl, self.Constants.API_V1_BRANCHES.value.format(project_key, repo_slug, page_number))
            res = self.session.get(url)
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch branches for '%s/%s': %s", project_key, repo_slug, err)
                return {}

            data = res.json()

            for element in data['values']:
                branch_name = element["displayId"]

                if element["isDefault"]:
                    continue

                if branch_name in self.PROTECTED_BRANCHES:
                    continue

                branches[branch_name] = element["latestCommit"]

            if data['isLastPage']:
                break

            page_number = data['nextPageStart']

        logging.info("Collected %d candidate branches for '%s/%s'", len(branches), project_key, repo_slug)
        return branches

    def delete_branch(self, project_key: str, repo_slug: str, branch_name: str):
        logging.info("Attempting to delete branch '%s' in '%s/%s'", branch_name, project_key, repo_slug)

        url = posixpath.join(self.baseurl, self.Constants.API_V1_DELETE_BRANCH.value.format(project_key, repo_slug))
        res = self.session.delete(url, json={"dryRun": False, "name": branch_name})
        try:
            res.raise_for_status()
        except requests.exceptions.HTTPError as err:
            logging.error("Failed to delete branch '%s' in '%s/%s': %s", branch_name, project_key, repo_slug, err)
            return

        logging.info("Successfully deleted branch '%s' in '%s/%s'", branch_name, project_key, repo_slug)

    def check_branch_stale(self, project_key: str, repo_slug: str, commit_hash: str, cutoff_date, branch_name) -> bool:
        logging.debug("Checking if branch '%s' is merged into protected branches", branch_name)

        for protected_branch in self.PROTECTED_BRANCHES:
            url = posixpath.join(self.baseurl, self.Constants.API_V1_COMPARE_BRANCHES.value.format(project_key, repo_slug, branch_name, protected_branch))
            res = self.session.get(url)
            if res.status_code == 404:
                logging.debug("Protected branch '%s' does not exist in '%s/%s'", protected_branch, project_key, repo_slug)
                continue
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.warning("Compare failed for '%s' -> '%s': %s", branch_name, protected_branch, err)
                continue
            data = res.json()
            if data.get('size', 0) == 0:
                logging.info("Branch '%s' already merged into '%s'", branch_name, protected_branch)
                return True

        url = posixpath.join(self.baseurl, self.Constants.API_V1_COMMITS.value.format(project_key, repo_slug, commit_hash))
        res = self.session.get(url)
        try:
            res.raise_for_status()
        except requests.exceptions.HTTPError as err:
            logging.error("Unable to fetch commit '%s' for '%s/%s': %s", commit_hash, project_key, repo_slug, err)
            return False

        data = res.json()
        commit_date = datetime.fromtimestamp(data['committerTimestamp'] / 1000, tz=timezone.utc).date()
        if commit_date > cutoff_date:
            return False

        logging.info("Branch '%s' is stale (last commit: %s)", branch_name, commit_date)
        return True


def get_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        logging.error("Required environment variable '%s' is not set", name)
        raise RuntimeError(f"Environment variable {name} must be set")
    return value
