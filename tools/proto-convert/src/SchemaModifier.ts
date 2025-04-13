import type {OpenAPIV3} from "openapi-types";
import {traverse, traverseSchema} from './utils/OpenApiTraverser';
import isEqual from 'lodash.isequal';
import {compressMultipleUnderscores, resolveObj} from './utils/helper';
import Logger from "./utils/logger";

// type SchemaObjectWithPropertyNames = OpenAPIV3.SchemaObject & {
//     propertyNames?: OpenAPIV3.SchemaObject | OpenAPIV3.ReferenceObject;
// };

export class SchemaModifier {
    logger: Logger
    root: OpenAPIV3.Document;
    constructor(root: OpenAPIV3.Document, logger: Logger = new Logger()) {
        this.root = root;
        this.logger = logger;
    }
    public modify(OpenApiSpec: OpenAPIV3.Document): any {
        traverse(OpenApiSpec, {
            onSchemaProperty: (schema) => {
                this.handleAdditionalPropertiesUndefined(schema)
                this.collapseOrMergeOneOfArray(schema)
            },
            onSchema: (schema, schemaName) => {
                if (!schema || this.isReferenceObject(schema)) return;
                this.handleOneOfConst(schema, schemaName)
                this.collapseOrMergeOneOfArray(schema)
                this.handleAdditionalPropertiesUndefined(schema)
                //this.CollapseOneOfObjectPropContainsTitleSchema(schema)
            },
        });
        return OpenApiSpec;
    }

    // Converts `additionalProperties: true` or `additionalProperties: {}` to `type: object`.
    // Example: { additionalProperties: true } -> { type: 'object' }
    handleAdditionalPropertiesUndefined(schema: OpenAPIV3.SchemaObject): void {
        if (schema.additionalProperties === true || ( typeof schema.additionalProperties === 'object' &&  this.isEmptyObjectSchema(schema.additionalProperties as OpenAPIV3.SchemaObject))) {
            schema.type = 'object';
            delete schema.additionalProperties;
        }
    }

    isReferenceObject(schema: any): schema is OpenAPIV3.ReferenceObject {
        return schema !== null && typeof schema === 'object' && '$ref' in schema;
    }

    // Converts `oneOf` schemas with `const` values to boolean types.
    // Example: oneof: [ {type: 'string', const: 'a'} ] to type: 'boolean' with title: 'schemaName_a'
    handleOneOfConst(schema: OpenAPIV3.SchemaObject, schemaName: string): void {
        if (schema.oneOf) {
            for (const item of schema.oneOf) {
                if (item && !('$ref' in item) && item.type === 'string' && 'const' in item) {
                    item.type = 'boolean';
                    item.title = compressMultipleUnderscores(`${schemaName}_${item.const}`);
                    delete item.const;
                }
            }
        }
    }
    isEmptyObjectSchema(schema: OpenAPIV3.SchemaObject): boolean {
        return (
            schema.type === 'object' &&
            !schema.properties &&
            !schema.allOf &&
            !schema.anyOf &&
            !schema.oneOf &&
            !('$ref' in schema)
        );
    }

    // Simplify schemas with `oneOf` by aggregating items.
    // If there are only two `oneOf` items and one matches an array schema, remove oneOf type and set type to array.
    // If there are more than two `oneOf` items and one matches an array schema, remove that item from `oneOf`.
    collapseOrMergeOneOfArray(schema: OpenAPIV3.SchemaObject): void{
        if (!('$ref' in schema) && Array.isArray(schema.oneOf)) {
            const oneOfs = schema.oneOf;

            const arraySet = new Set<string>();
            var deleteIndx = -1;

            for (const oneOf of oneOfs) {
                if (this.isArraySchemaObject(oneOf)) {
                    const { type, $ref, additionalProperties} = oneOf.items as any;
                    const oneOfStr = JSON.stringify({ type, $ref, additionalProperties});
                    arraySet.add(oneOfStr)
                }
            }
            for (const oneOf of oneOfs) {
                const { type, $ref, additionalProperties} = oneOf as any;
                const oneOfStr = JSON.stringify({ type, $ref, additionalProperties});
                if (arraySet.has(oneOfStr)) {
                    deleteIndx = oneOfs.findIndex(item => isEqual(item, oneOf));
                    oneOfs.splice(deleteIndx, 1);
                }
            }
            this.collapseSingleItemOneOf(schema);
        }
    }

    collapseSingleItemOneOf(schema: OpenAPIV3.SchemaObject): void {
        if (Array.isArray(schema.oneOf) && schema.oneOf.length === 1) {
            const [singleOneOf] = schema.oneOf as OpenAPIV3.SchemaObject[];
            Object.assign(schema, singleOneOf);
            delete schema.oneOf;
        }
    }

    isArraySchemaObject(schema: any): schema is OpenAPIV3.ArraySchemaObject {
        return (
            typeof schema === 'object' &&
            schema !== null &&
            schema.type === 'array' &&
            'items' in schema
        );
    }

    // transformPropertyNamesSchema(schema: SchemaObjectWithPropertyNames): void {
    //     if (schema.type === 'object' &&
    //         typeof schema.additionalProperties === 'object' &&
    //         !Array.isArray(schema.additionalProperties) &&
    //         schema.minProperties === 1 &&
    //         schema.maxProperties === 1){
    //         //     const { title, ...restOfAdditionalProps } = schema.additionalProperties as OpenAPIV3.SchemaObject;
    //         //
    //         //     // schema.properties = {
    //         //     //     field_key: { type: 'string' },
    //         //     //     [title as string]: restOfAdditionalProps
    //         //     // };
    //         //     //
    //         //     // schema.required = ['field_key', title as string];
    //         //     //
    //         //     // delete schema.propertyNames;
    //         //     // delete schema.additionalProperties;
    //         //     //
    //         //     // delete schema.minProperties;
    //         //     // delete schema.maxProperties;
    //         //     console.log(schema)
    //         //     this.cnt++
    //     }
    // }

    CollapseOneOfObjectPropContainsTitleSchema(schema: OpenAPIV3.SchemaObject): void {
        // TODO: might need to handle oneOf more than 2
        if (!Array.isArray(schema.oneOf) || schema.oneOf.length !== 2) {
            return;
        }
        const[first, second] = schema.oneOf;
        if (this.tryCollapseIfMatching(schema, first, second, 0)) return;
        if (this.tryCollapseIfMatching(schema, second, first, 1)) return;
    }

    private tryCollapseIfMatching(
        schema: OpenAPIV3.SchemaObject,
        maybeSimple: OpenAPIV3.SchemaObject | OpenAPIV3.ReferenceObject,
        maybeComplex: OpenAPIV3.SchemaObject | OpenAPIV3.ReferenceObject,
        indexOfSimple: number
    ): boolean {
        let foundMath = false;
        if (! ('title' in maybeSimple && typeof (maybeSimple.title) === 'string')) {
            return false;
        }
        let titleContent = JSON.stringify(maybeSimple);
        let nameStr = maybeSimple.title;

        const complexObject = resolveObj(maybeComplex, this.root);
        if (!complexObject) {
            return false;
        }
        if (Array.isArray(complexObject.allOf)) {
            for (const sub of complexObject.allOf) {
                const subObject = resolveObj(sub, this.root);
                if (subObject && subObject.properties && subObject.properties[nameStr]) {
                    const propSchema =  subObject.properties[nameStr];
                    if ('$ref' in propSchema && propSchema.$ref && titleContent.includes(propSchema.$ref)) {
                        foundMath = true;
                    } else if ('type' in propSchema && propSchema.type && titleContent.includes(propSchema.type)) {
                        foundMath = true;
                    }
                }
            }
        } else if (complexObject.type === 'object' && complexObject.properties) {
            if(complexObject.properties[nameStr] && '$ref' in complexObject.properties[nameStr]) {
                const propSchema =  complexObject.properties[nameStr];
                if ('$ref' in propSchema && propSchema.$ref && titleContent.includes(propSchema.$ref)) {
                    foundMath = true;
                } else if ('type' in propSchema && typeof propSchema.type === 'string' && titleContent.includes(propSchema.type)) {
                    foundMath = true;
                }
            }
        }
        if (foundMath) {
            schema.oneOf?.splice(indexOfSimple, 1);
            const [remaining] = schema.oneOf || [];
            delete schema.oneOf;
            Object.assign(schema, remaining);
            return true;
        }
        return false;
    }
}
