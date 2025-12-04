#!/usr/bin/env python3
"""
Script to find unused messages and enums in proto schema files.
Analyzes which messages and enums are defined but never referenced by other messages.
"""

import re
from collections import defaultdict
from pathlib import Path
from typing import Dict, Set, Tuple

class ProtoParser:
    """Parse protobuf files and extract message definitions and dependencies."""

    def __init__(self, filepath: str):
        self.filepath = filepath
        self.content = Path(filepath).read_text()
        self.messages: Dict[str, Dict] = {}
        self.enums: Dict[str, Dict] = {}
        self.parse()

    def parse(self):
        """Parse proto file and extract messages and enums."""
        # Remove comments
        content = re.sub(r'//.*?$', '', self.content, flags=re.MULTILINE)
        content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

        # Extract messages
        message_pattern = r'message\s+(\w+)\s*\{(.*?)\n\}'
        for match in re.finditer(message_pattern, content, re.DOTALL):
            msg_name = match.group(1)
            msg_body = match.group(2)
            self.messages[msg_name] = self._parse_message_body(msg_body)

        # Extract enums
        enum_pattern = r'enum\s+(\w+)\s*\{(.*?)\n\}'
        for match in re.finditer(enum_pattern, content, re.DOTALL):
            enum_name = match.group(1)
            enum_body = match.group(2)
            self.enums[enum_name] = self._parse_enum_body(enum_body)

    def _parse_enum_body(self, body: str) -> Dict:
        """Parse enum body to extract values."""
        values = {}
        # Remove comments
        body = re.sub(r'//.*?$', '', body, flags=re.MULTILINE)

        # Parse enum values: NAME = number;
        value_pattern = r'(\w+)\s*=\s*(\d+)'
        for match in re.finditer(value_pattern, body):
            value_name = match.group(1)
            value_number = int(match.group(2))
            values[value_name] = value_number

        return {'values': values}

    def _parse_message_body(self, body: str) -> Dict:
        """Parse message body to extract fields."""
        fields = {}

        # Remove nested messages from body for field parsing
        body_without_nested = re.sub(r'message\s+\w+\s*\{.*?\n\s*\}', '', body, flags=re.DOTALL)
        body_without_nested = re.sub(r'oneof\s+\w+\s*\{.*?\n\s*\}', '', body_without_nested, flags=re.DOTALL)

        # Parse regular fields
        field_pattern = r'(optional|repeated|oneof)?\s*(\w+(?:<\w+,\s*\w+>)?)\s+(\w+)\s*=\s*(\d+)'
        for match in re.finditer(field_pattern, body_without_nested):
            field_type = match.group(2)
            fields[match.group(3)] = field_type

        # Parse oneof fields
        oneof_pattern = r'oneof\s+(\w+)\s*\{(.*?)\n\s*\}'
        for match in re.finditer(oneof_pattern, body, re.DOTALL):
            oneof_body = match.group(2)
            field_pattern_oneof = r'(\w+(?:<\w+,\s*\w+>)?)\s+(\w+)\s*=\s*(\d+)'
            for field_match in re.finditer(field_pattern_oneof, oneof_body):
                fields[field_match.group(2)] = field_match.group(1)

        return {'fields': fields}

    def get_messages(self) -> Dict[str, Dict]:
        """Return all messages."""
        return self.messages

    def get_enums(self) -> Dict[str, Dict]:
        """Return all enums."""
        return self.enums

    def get_message_dependencies(self) -> Dict[str, Set[str]]:
        """Get all message types used by each message."""
        dependencies = defaultdict(set)

        for msg_name, msg_data in self.messages.items():
            fields = msg_data.get('fields', {})
            for field_name, field_type in fields.items():
                # Extract the actual type from generic types
                # e.g., "map<string, Type>" -> "Type"
                field_type = re.sub(r'map<.*?,\s*(\w+)>', r'\1', field_type)
                # Remove generic brackets if any
                field_type = re.sub(r'<.*?>', '', field_type)
                # Only track if it's likely a message (starts with uppercase)
                if field_type and field_type[0].isupper():
                    dependencies[msg_name].add(field_type)

        return dependencies

    def get_enum_dependencies(self) -> Dict[str, Set[str]]:
        """Get all enum types used by each message."""
        dependencies = defaultdict(set)

        for msg_name, msg_data in self.messages.items():
            fields = msg_data.get('fields', {})
            for field_name, field_type in fields.items():
                # Extract the actual type from generic types
                field_type = re.sub(r'map<.*?,\s*(\w+)>', r'\1', field_type)
                field_type = re.sub(r'<.*?>', '', field_type)
                # Check if this type is an enum in the current file
                if field_type in self.enums:
                    dependencies[msg_name].add(field_type)

        return dependencies


def find_unused_messages_and_enums():
    """Find messages and enums that are defined but never used."""
    schema_dir = Path("/home/user/opensearch-protobufs/protos/schemas")
    schema_files = {
        'common': schema_dir / 'common.proto',
        'document': schema_dir / 'document.proto',
        'search': schema_dir / 'search.proto'
    }

    # Parse all schema files
    parsers = {}
    all_dependencies = defaultdict(set)
    all_messages = {}
    all_enums = {}

    print("\n" + "="*80)
    print("FINDING UNUSED MESSAGES AND ENUMS IN SCHEMA FILES")
    print("="*80 + "\n")

    for schema_name, schema_file in schema_files.items():
        if schema_file.exists():
            print(f"📖 Parsing {schema_file.name}...")
            parser = ProtoParser(str(schema_file))
            parsers[schema_name] = parser
            messages = parser.get_messages()
            enums = parser.get_enums()
            print(f"   Found {len(messages)} messages, {len(enums)} enums")

            # Track all messages and enums by their source file
            for msg_name in messages:
                all_messages[msg_name] = schema_name
            for enum_name in enums:
                all_enums[enum_name] = schema_name

            # Get dependencies
            dependencies = parser.get_message_dependencies()
            all_dependencies.update(dependencies)

    # Now check enum usage across all files
    # First collect all field types used in all messages
    all_field_types = set()
    for msg_name, msg_data in all_messages.items():
        # We need to get the message data from the correct parser
        source_file = all_messages[msg_name]
        for schema_name, parser in parsers.items():
            if schema_name == source_file and msg_name in parser.messages:
                fields = parser.messages[msg_name].get('fields', {})
                for field_name, field_type in fields.items():
                    # Normalize field types
                    field_type = re.sub(r'map<.*?,\s*(\w+)>', r'\1', field_type)
                    field_type = re.sub(r'<.*?>', '', field_type)
                    all_field_types.add(field_type)
                break

    # Find all referenced enums
    referenced_enums = set()
    for field_type in all_field_types:
        if field_type in all_enums:
            referenced_enums.add(field_type)

    # Entry points that are intentionally used
    entry_points = {'SearchResponse', 'SearchRequest', 'BulkRequest', 'BulkResponse'}

    # Find all messages that are referenced
    referenced_messages = set()
    for deps in all_dependencies.values():
        referenced_messages.update(deps)

    # Find unused messages (defined but never referenced)
    unused_messages = defaultdict(list)
    for msg_name, source_file in sorted(all_messages.items()):
        if msg_name not in referenced_messages and msg_name not in entry_points:
            unused_messages[source_file].append(msg_name)

    # Find unused enums (defined but never referenced)
    unused_enums = defaultdict(list)
    for enum_name, source_file in sorted(all_enums.items()):
        if enum_name not in referenced_enums:
            unused_enums[source_file].append(enum_name)

    # Print message results
    print("\n" + "="*80)
    print("UNUSED MESSAGES SUMMARY")
    print("="*80 + "\n")

    if unused_messages:
        total_unused = sum(len(msgs) for msgs in unused_messages.values())
        print(f"📋 Found {total_unused} unused messages:\n")

        for schema_name in sorted(unused_messages.keys()):
            msgs = unused_messages[schema_name]
            print(f"{schema_name}.proto ({len(msgs)} unused messages):")
            for msg in sorted(msgs):
                print(f"  - {msg}")
            print()
    else:
        print("✅ No unused messages found!\n")

    # Print enum results
    print("\n" + "="*80)
    print("UNUSED ENUMS SUMMARY")
    print("="*80 + "\n")

    if unused_enums:
        total_unused = sum(len(enums) for enums in unused_enums.values())
        print(f"📋 Found {total_unused} unused enums:\n")

        for schema_name in sorted(unused_enums.keys()):
            enums = unused_enums[schema_name]
            print(f"{schema_name}.proto ({len(enums)} unused enums):")
            for enum in sorted(enums):
                print(f"  - {enum}")
            print()
    else:
        print("✅ No unused enums found!\n")

    print("="*80 + "\n")

    # Print statistics
    print("\n" + "="*80)
    print("STATISTICS")
    print("="*80 + "\n")

    total_messages = len(all_messages)
    total_enums = len(all_enums)
    total_referenced_msgs = len(referenced_messages)
    total_referenced_enums = len(referenced_enums)
    total_entry_points = len(entry_points & set(all_messages.keys()))
    total_unused_msgs = sum(len(msgs) for msgs in unused_messages.values())
    total_unused_enums = sum(len(enums) for enums in unused_enums.values())

    print(f"MESSAGES:")
    print(f"  Total defined:          {total_messages}")
    print(f"  Entry points:           {total_entry_points}")
    print(f"  Referenced:             {total_referenced_msgs}")
    print(f"  Unused:                 {total_unused_msgs}")
    if total_messages > 0:
        print(f"  Usage rate:             {(total_referenced_msgs + total_entry_points) / total_messages * 100:.1f}%")

    print(f"\nENUMS:")
    print(f"  Total defined:          {total_enums}")
    print(f"  Referenced:             {total_referenced_enums}")
    print(f"  Unused:                 {total_unused_enums}")
    if total_enums > 0:
        print(f"  Usage rate:             {total_referenced_enums / total_enums * 100:.1f}%")

    print("\n" + "="*80 + "\n")


if __name__ == "__main__":
    find_unused_messages_and_enums()
