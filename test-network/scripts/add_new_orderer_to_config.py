# This is a sample Python script.

# Press ⌃R to execute it or replace it with your code.
# Press Double ⇧ to search everywhere for classes, files, tool windows, actions, and settings.
import argparse
import base64
import copy
import json
import math
from typing import Any


def parse_args():
    parser = argparse.ArgumentParser(
        prog='Config Update',
        description='What the program does',
        epilog='Text at the bottom of help')
    parser.add_argument('config_path', type=str)
    parser.add_argument('updated_config_path', type=str)
    parser.add_argument('-a', '--address', type=str, required=True)
    parser.add_argument('-i', '--identity', type=str, required=True)
    parser.add_argument('-s', '--server-cert', type=str, required=True)
    parser.add_argument('-c', '--client-cert', type=str, required=True)
    return parser.parse_args()


def _pem_file_to_base64(path: str) -> str:
    with open(path, 'rb') as binary_file:
        binary_file_data = binary_file.read()
        base64_encoded_data = base64.b64encode(binary_file_data)
        return base64_encoded_data.decode('utf-8')


def _log_update(name: str, old: Any, new: Any) -> None:
    print('=' * 50)
    print(f'Updating {name}:')
    print(f'{old}')
    print(">" * 25)
    print(f'{new}')
    print('=' * 50)


def _calculate_bft_quorum(n: int) -> int:
    f = int((n - 1) / 3)
    return int(math.ceil((n + f + 1) / 2))


def update_config(config_path: str, updated_config_path: str, address: str, identity_pem_path: str, server_pem_path: str, client_pem_path: str):
    with open(config_path, 'r') as f:
        config = json.load(f)
    identity = _pem_file_to_base64(identity_pem_path)
    client_cert = _pem_file_to_base64(client_pem_path)
    server_cert = _pem_file_to_base64(server_pem_path)
    host, port = address.split(':')

    addresses = config['channel_group']['groups']['Orderer']['groups']['OrdererOrg']['values']['Endpoints']['value']['addresses']
    addresses_before_update = copy.deepcopy(addresses)
    original_orderers_count = len(addresses_before_update)
    addresses.append(f'{addresses[0].split(":")[0]}:{port}')
    new_orderers_count = len(addresses)
    _log_update('addresses', addresses_before_update, addresses)

    identities = config['channel_group']['groups']['Orderer']['policies']['BlockValidation']['policy']['value']['identities']
    identities_before_update = copy.deepcopy(identities)
    new_identity = copy.deepcopy(identities[0])
    new_identity['principal']['id_bytes'] = identity
    identities.append(new_identity)
    _log_update('block validation identities', identities_before_update, identities)

    rule = config['channel_group']['groups']['Orderer']['policies']['BlockValidation']['policy']['value'][
        'rule']
    rule_before_update = copy.deepcopy(rule)
    rule['n_out_of']['n'] = _calculate_bft_quorum(new_orderers_count)
    rule['n_out_of']['rules'].append({'signed_by': new_orderers_count - 1})
    _log_update('block validation rules', rule_before_update, rule)

    consenter_mapping = config['channel_group']['groups']['Orderer']['values']['Orderers']['value']['consenter_mapping']
    consenter_mapping_before_update = copy.deepcopy(consenter_mapping)
    consenter_mapping.append({
        'client_tls_cert': client_cert,
        'host': host,
        'id': new_orderers_count,
        'identity': identity,
        'msp_id': consenter_mapping[0]['msp_id'],
        'port': port,
        'server_tls_cert': server_cert
    })
    _log_update('consenter_mapping', consenter_mapping_before_update, consenter_mapping)

    with open(updated_config_path, 'w') as f:
        json.dump(config, f)

# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    args = parse_args()
    update_config(args.config_path, args.updated_config_path, args.address, args.identity, args.server_cert, args.client_cert)

# See PyCharm help at https://www.jetbrains.com/help/pycharm/
