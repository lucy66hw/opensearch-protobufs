import _ from 'lodash';
import * as semver from 'semver';
import Logger from './utils/logger';
import { deleteMatchingKeys } from './utils/helper';
import type { OpenAPIV3 } from 'openapi-types';

/**
 * Processes version-related vendor extensions:
 * - x-version-added: Removes fields added after current version
 * - x-version-deprecated: Removes fields deprecated in current version or earlier
 * - x-version-removed: Removes fields removed before current version
 */
export class VersionProcessor {
  private _logger: Logger;
  private _spec: OpenAPIV3.Document;
  private _target_version: string;

  constructor(spec: OpenAPIV3.Document, logger: Logger) {
    this._spec = spec;
    this._logger = logger;
    this._target_version = '';
  }


  process(currentVersion: string): OpenAPIV3.Document {
    this._target_version = currentVersion;
    this._logger.info(`Processing version constraints for OpenSearch ${currentVersion} ...`);
    deleteMatchingKeys(this._spec, (item: any) => {
      if (_.isObject(item) && this.#exclude_per_semver(item)) {
        return true;
      }
      return false;
    });
    this._logger.info('Version processing complete');
    return this._spec;
  }

  #exclude_per_semver(obj: any): boolean {
    if (this._target_version == undefined) return false

    const x_version_added = semver.coerce(obj['x-version-added'] as string)
    const x_version_deprecated = semver.coerce(obj['x-version-deprecated'] as string)
    const x_version_removed = semver.coerce(obj['x-version-removed'] as string)

    // If field was added in a future version, exclude it
    if (x_version_added !== null && x_version_added !== undefined && !semver.satisfies(this._target_version, `>=${x_version_added.toString()}`)) {
      return true
    }

    // If field was deprecated in current version or earlier, exclude it
    if (x_version_deprecated !== null && x_version_deprecated !== undefined && !semver.satisfies(this._target_version, `<${x_version_deprecated.toString()}`)) {
      return true
    }

    // If field was removed in current version or earlier, exclude it
    if (x_version_removed !== null && x_version_removed !== undefined && !semver.satisfies(this._target_version, `<${x_version_removed.toString()}`)) {
      return true
    }

    return false
  }
}
