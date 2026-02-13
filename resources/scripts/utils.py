import logging
import posixpath
import requests
import urllib3
import os
import enum
from datetime import datetime, timezone


urllib3.disable_warnings()

# TODO: Refactor this code
class BitbucketAPIClient():
    def __init__(self, baseurl: str, username: str, password: str):
        self.session = requests.session()
        self.baseurl = baseurl
        self.username = username
        self.password = password
        self.session.auth = (self.username, self.password)
        self.session.headers = {'Content-Type': 'application/json'}
        self.session.verify = False

    TARGET_PROJECT_NAMES = ['CDT Infrastructure']
    # TODO: Some repositories must contain main or not contain staging branch
    PROTECTED_BRANCHES = ['master', 'staging', 'main']

    class Constants(enum.Enum):
        API_V1_PROJECTS = 'rest/api/1.0/projects?name={}'
        API_V1_REPOSITORIES = 'rest/api/1.0/projects/{}/repos?start={}'
        API_V1_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/branches?start={}'
        API_V1_COMMITS = 'rest/api/1.0/projects/{}/repos/{}/commits/{}'
        API_V1_COMPARE_BRANCHES = 'rest/api/1.0/projects/{}/repos/{}/compare/commits?from={}&to={}'
        API_V1_DELETE_BRANCH = 'rest/branch-utils/1.0/projects/{}/repos/{}/branches'

    def list_project_keys(self) -> list[str]:
        project_keys = []
        for project_name in self.TARGET_PROJECT_NAMES:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_PROJECTS.value.format(project_name)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                # TODO: Find better message
                logging.error("Unable to fetch key for %s project, error: %s", project_name, err)
                continue
            data = res.json()
            if not data.get('values') or len(data.get('values')) > 1:
                # TODO: Find better message
                logging.error("Empty or too much values for %s project", project_name)
                continue
            project_keys.append(data["values"][0]["key"])
        return project_keys

    def list_project_repo_slugs(self, project_key: str) -> list[str]:
        page_number = 0
        repo_slugs = []
        while True:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_REPOSITORIES.value.format(project_key, page_number)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch project repositories for '%s' project, error: %s", project_key, err)
                return []
            data = res.json()
            for element in data['values']:
                repo_slugs.append(element['slug'])
            if data['isLastPage']:
                break
            page_number = data['nextPageStart']
        return repo_slugs

    def list_repo_branches(self, project_key: str, repo_slug: str):
        page_number = 0
        branches = {}
        while True:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_BRANCHES.value.format(project_key, repo_slug, page_number)))
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                logging.error("Unable to fetch branches for '%s' repository, error: %s", repo_slug, err)
                return {}
            data = res.json()
            for element in data['values']:
                # if branch is not default and not staging or master, then we can try to kill it
                if not element["isDefault"] and element["displayId"] not in self.PROTECTED_BRANCHES:
                    branches[element["displayId"]] = element["latestCommit"]
            if data['isLastPage']:
                break
            page_number = data['nextPageStart']
        return branches

    def delete_branch(self, project_key: str, repo_slug: str, branch_name: str):
        logging.info(f"Trying to delete branch '{branch_name}' for '{project_key}/{repo_slug}'")
        res = self.session.delete(posixpath.join(self.baseurl, self.Constants.API_V1_DELETE_BRANCH.value.format(project_key, repo_slug)), json={"dryRun": False, "name": branch_name})
        try:
            res.raise_for_status()
        except requests.exceptions.HTTPError as err:
            logging.error("Unable to delete branch '%s' for '%s/%s', error: %s", branch_name, project_key, repo_slug, err)
            return
        logging.info(f"Deletion completed. branch '{branch_name}' for '{project_key}/{repo_slug}'")

    def _is_branch_stale(self, project_key: str, repo_slug: str, commit_hash: str, cutoff_date, branch_name) -> bool:
        for protected_branch in self.PROTECTED_BRANCHES:
            res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_COMPARE_BRANCHES.value.format(project_key, repo_slug, branch_name, protected_branch)))
            if res.status_code == 404:
                # TODO: Delete this log
                logging.info("Protected branch %s does not exist in %s/%s. Skipping.", protected_branch, project_key, repo_slug)
                continue
            try:
                res.raise_for_status()
            except requests.exceptions.HTTPError as err:
                # TODO: Delete this log
                logging.error("Unable to fetch branch info: %s", err)
                continue
            data = res.json()
            # if it is 0, then it can be deleted, if it is not, then it must not be deleted
            if data.get('size', 0) == 0:
                # TODO: Delete this log
                logging.info("Branch %s is already merged into %s", branch_name, protected_branch)
                return True

        logging.info("Branch %s seems active", branch_name)
        # check if commit.committerTimestamp is valid
        res = self.session.get(posixpath.join(self.baseurl, self.Constants.API_V1_COMMITS.value.format(project_key, repo_slug, commit_hash)))
        try:
            res.raise_for_status()
        except requests.exceptions.HTTPError as err:
            # if some shit happens -> just mark the branch as active
            logging.error("Unable to fetch repository commit, error: %s", err)
            return False
        data = res.json()
        commit_date = datetime.fromtimestamp(data['committerTimestamp'] / 1000, tz=timezone.utc).date()
        if commit_date > cutoff_date:
            # if branch commit date is older than cutoff date - the branch is active
            logging.info("Branch %s is totally active", branch_name)
            return False
        # otherwise the branch is stale
        logging.info("Branch %s is stale. Last time: %s", branch_name, commit_date)
        return True




def get_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Environment variable {name} must be set")
    return value
