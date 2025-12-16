/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import { OpenAPIV3 } from 'openapi-types';

// ============================================================================
// Types and Constants
// ============================================================================

/** HTTP methods supported by OpenAPI */
export const HTTP_METHODS: OpenAPIV3.HttpMethods[] = Object.values(OpenAPIV3.HttpMethods);

/** Schema object types */
export type SchemaObjectType = OpenAPIV3.ArraySchemaObjectType | OpenAPIV3.NonArraySchemaObjectType;
export const SCHEMA_OBJECT_TYPES: Set<SchemaObjectType> = new Set(['array', 'boolean', 'object', 'number', 'string', 'integer']);
export const SCHEMA_NUMERIC_TYPES: Set<SchemaObjectType> = new Set(['number', 'integer']);
export const SCHEMA_NUMBER_FORMATS: Set<string> = new Set(['float', 'double']);
export const SCHEMA_INTEGER_FORMATS: Set<string> = new Set(['int32', 'int64']);

/** A schema that may be a reference */
export type MaybeRef<O extends object> = O | OpenAPIV3.ReferenceObject;

/** Type utility to extract keys matching a specific type */
export type KeysMatching<T extends object, V> = {
    [K in keyof T]-?: T[K] extends V ? K : never
}[keyof T];

/** Validation error type */
export interface ValidationError {
    file: string;
    location: string;
    message: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

/** Check if an object is a $ref */
export function is_ref<O extends object>(o: MaybeRef<O>): o is OpenAPIV3.ReferenceObject {
    return o != null && typeof o === 'object' && '$ref' in o;
}

/** Check if a schema is an array schema */
export function is_array_schema(schema: OpenAPIV3.SchemaObject): schema is OpenAPIV3.ArraySchemaObject {
    return schema.type === 'array';
}

/** Check if a schema is a primitive type */
export function is_primitive_schema(schema: OpenAPIV3.SchemaObject): boolean {
    return schema.type === 'boolean' ||
        schema.type === 'integer' ||
        schema.type === 'number' ||
        schema.type === 'string';
}

/** Check if a schema is a string const schema */
export function is_string_const_schema(schema: OpenAPIV3.SchemaObject): boolean {
    return schema.type === 'string' &&
        ((schema as any).const !== undefined ||
         (schema.enum !== undefined && schema.enum.length === 1));
}

/** Check if a schema is an enum schema */
export function is_enum_schema(schema: OpenAPIV3.SchemaObject): boolean {
    if (schema.oneOf !== undefined && schema.oneOf.length > 0) {
        let enum_count = 0;
        let boolean_count = 0;
        let total_count = 0;

        for (const s of schema.oneOf) {
            if (!is_ref(s)) {
                if (s.type === 'null' as any) {
                    continue;
                } else if (s.type === 'boolean') {
                    boolean_count += 1;
                } else if (is_enum_schema(s) || is_string_const_schema(s)) {
                    enum_count += 1;
                }
            }
            total_count += 1;
        }

        return enum_count === total_count || (boolean_count === 1 && enum_count === total_count - 1);
    }
    return schema.type === 'string' && (schema.enum !== undefined && schema.enum.length > 0);
}

// ============================================================================
// SpecificationContext
// ============================================================================

/**
 * Tracks the current location within an OpenAPI specification during traversal.
 * Useful for error reporting and debugging.
 */
export class SpecificationContext {
    private readonly _file: string;
    private readonly _location: string[];

    constructor(file: string = '', location: string[] = ['#']) {
        this._file = file;
        this._location = location;
    }

    parent(): SpecificationContext {
        if (this._location.length <= 1) return this;
        return new SpecificationContext(this._file, this._location.slice(0, -1));
    }

    child(key: string): SpecificationContext {
        return new SpecificationContext(this._file, [...this._location, key]);
    }

    error(message: string): ValidationError {
        return { file: this._file, location: this.location, message };
    }

    get file(): string {
        return this._file;
    }

    get location(): string {
        return this._location
            .map(k => k
                .replaceAll('~', '~0')
                .replaceAll('/', '~1'))
            .join('/');
    }

    get key(): string {
        return this._location[this._location.length - 1];
    }

    get keys(): string[] {
        return [...this._location];
    }
}

// ============================================================================
// Visitor Helpers
// ============================================================================

type VisitorCallback<T> = (ctx: SpecificationContext, o: NonNullable<T>) => void;
type SchemaVisitorCallback = VisitorCallback<MaybeRef<OpenAPIV3.SchemaObject>>;

function visit<Parent, Key extends keyof Parent>(
    ctx: SpecificationContext,
    parent: Parent,
    key: Key,
    visitor: VisitorCallback<Parent[Key]>
): void {
    const child = parent[key];
    if (child == null) return;
    visitor(ctx.child(key as string), child);
}

type EnumerableKeys<T extends object> = KeysMatching<T, Record<string, unknown> | undefined> | KeysMatching<T, ArrayLike<unknown> | undefined>;
type ElementOf<T> = T extends Record<string, infer V> ? V : T extends ArrayLike<infer V> ? V : never;

function visit_each<Parent extends object, Key extends EnumerableKeys<Parent>>(
    ctx: SpecificationContext,
    parent: Parent,
    key: Key,
    visitor: VisitorCallback<ElementOf<Parent[Key]>>
): void {
    const children = parent[key];
    if (children == null) return;
    ctx = ctx.child(key as string);
    Object.entries<ElementOf<Parent[Key]>>(children).forEach(([key, child]) => {
        if (child == null) return;
        visitor(ctx.child(key), child);
    });
}

// ============================================================================
// SpecificationVisitor
// ============================================================================

/**
 * Base visitor for traversing OpenAPI specifications.
 * Extend this class and override methods to customize behavior.
 */
export class SpecificationVisitor {
    visit_specification(ctx: SpecificationContext, specification: OpenAPIV3.Document): void {
        visit_each(ctx, specification, 'paths', this.visit_path.bind(this));
        visit(ctx, specification, 'components', this.visit_components.bind(this));
    }

    visit_path(ctx: SpecificationContext, path: OpenAPIV3.PathItemObject): void {
        visit_each(ctx, path, 'parameters', this.visit_parameter.bind(this));
        for (const method of HTTP_METHODS) {
            visit(ctx, path, method, this.visit_operation.bind(this));
        }
    }

    visit_operation(ctx: SpecificationContext, operation: OpenAPIV3.OperationObject): void {
        visit_each(ctx, operation, 'parameters', this.visit_parameter.bind(this));
        visit(ctx, operation, 'requestBody', this.visit_request.bind(this));
        visit_each(ctx, operation, 'responses', this.visit_response.bind(this));
    }

    visit_components(ctx: SpecificationContext, components: OpenAPIV3.ComponentsObject): void {
        visit_each(ctx, components, 'parameters', this.visit_parameter.bind(this));
        visit_each(ctx, components, 'requestBodies', this.visit_request.bind(this));
        visit_each(ctx, components, 'responses', this.visit_response.bind(this));
        visit_each(ctx, components, 'schemas', this.visit_schema.bind(this));
    }

    visit_parameter(ctx: SpecificationContext, parameter: MaybeRef<OpenAPIV3.ParameterObject>): void {
        if (is_ref(parameter)) return;
        visit(ctx, parameter, 'schema', this.visit_schema.bind(this));
    }

    visit_request(ctx: SpecificationContext, request: MaybeRef<OpenAPIV3.RequestBodyObject>): void {
        if (is_ref(request)) return;
        visit_each(ctx, request, 'content', this.visit_media_type.bind(this));
    }

    visit_response(ctx: SpecificationContext, response: MaybeRef<OpenAPIV3.ResponseObject>): void {
        if (is_ref(response)) return;
        visit_each(ctx, response, 'content', this.visit_media_type.bind(this));
    }

    visit_media_type(ctx: SpecificationContext, media_type: OpenAPIV3.MediaTypeObject): void {
        visit(ctx, media_type, 'schema', this.visit_schema.bind(this));
    }

    visit_schema(ctx: SpecificationContext, schema: MaybeRef<OpenAPIV3.SchemaObject>): void {
        if (is_ref(schema)) return;

        if (is_array_schema(schema)) {
            visit(ctx, schema, 'items', this.visit_schema.bind(this));
        }

        visit(ctx, schema, 'additionalProperties', (ctx, v) => {
            if (typeof v !== 'object') return;
            this.visit_schema(ctx, v as MaybeRef<OpenAPIV3.SchemaObject>);
        });

        visit_each(ctx, schema, 'properties', this.visit_schema.bind(this));
        visit_each(ctx, schema, 'allOf', this.visit_schema.bind(this));
        visit_each(ctx, schema, 'anyOf', this.visit_schema.bind(this));
        visit_each(ctx, schema, 'oneOf', this.visit_schema.bind(this));

        if ('not' in schema && schema.not) {
            visit(ctx, schema, 'not', this.visit_schema.bind(this));
        }
    }
}

/**
 * A visitor that applies a callback to every schema visited.
 */
export class SchemaVisitor extends SpecificationVisitor {
    private readonly _callback: SchemaVisitorCallback;

    constructor(callback: SchemaVisitorCallback) {
        super();
        this._callback = callback;
    }

    visit_schema(ctx: SpecificationContext, schema: MaybeRef<OpenAPIV3.SchemaObject>): void {
        super.visit_schema(ctx, schema);
        this._callback(ctx, schema);
    }
}

/**
 * Convenience function to traverse a specification with a visitor.
 */
export function traverseSpec(
    spec: OpenAPIV3.Document,
    visitor: SpecificationVisitor
): void {
    visitor.visit_specification(new SpecificationContext(), spec);
}
