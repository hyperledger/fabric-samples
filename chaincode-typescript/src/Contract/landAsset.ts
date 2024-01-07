import {Object, Property} from 'fabric-contract-api';

@Object()
export class Asset {
    @Property()
    public ID: string;

    @Property()
    public Size: number;

    @Property()
    public Owner: string;

    @Property()
    public TimeStamp: number;
}
