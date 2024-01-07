import {Object, Property} from 'fabric-contract-api';

@Object()
export class User {
    @Property()
    public userName: string;

    @Property()
    public Approved: boolean;

    @Property()
    public CreationTime: number;
}
