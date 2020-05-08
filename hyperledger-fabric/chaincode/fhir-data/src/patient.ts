interface ICoding {
    system?: string;
    version?: string;
    code?: string;
    display?: string;
    userSelected?: boolean;
}

interface IType {
    coding?: ICoding[];
    text?: string;
}

interface IPeriod {
    start?: string;
    end?: string;
}

interface IIdentifier {
    use?: string;
    type?: IType;
    system?: string;
    value?: string;
    period?: IPeriod;
}

interface ISecurity {
    system?: string;
    version?: string;
    code?: string;
    display?: string;
    userSelected?: boolean;
}

interface ITag {
    system?: string;
    version?: string;
    code?: string;
    display?: string;
    userSelected?: boolean;
}

interface IMeta {
    versionId?: string;
    lastUpdated?: string;
    source?: string;
    profile?: string[];
    security?: ISecurity[];
    tag?: ITag[];
}

interface IText {
    status?: string;
    div?: string;
}

interface IName {
    use?: string;
    text?: string;
    family?: string;
    given?: string[];
    prefix?: string[];
    suffix?: string[];
    period?: IPeriod;
}

interface ITelecom {
    system?: string;
    value?: string;
    use?: string;
    rank?: number;
    period?: IPeriod;
}

interface IAddress {
    use?: string;
    type?: string;
    text?: string;
    line?: string[];
    city?: string;
    district?: string;
    state?: string;
    postalCode?: string;
    country?: string;
    period?: IPeriod;
}

interface IMaritalStatus {
    coding?: ICoding[];
    text?: string;
}

interface IPhoto {
    contentType?: string;
    language?: string;
    data?: string;
    url?: string;
    size?: number;
    hash?: string;
    title?: string;
    creation?: string;
}

interface IRelationship {
    coding?: ICoding[];
    text?: string;
}

interface IOrganization {
    reference?: string;
    type?: string;
    identifier?: IIdentifier;
    display?: string;
}

interface IContact {
    relationship?: IRelationship[];
    name?: IName;
    telecom?: ITelecom[];
    address?: IAddress;
    gender: string;
    organization?: IOrganization;
    period?: IPeriod;
}

interface ILanguage {
    coding?: ICoding[];
    text?: string;
}

interface ICommunication {
    language?: ILanguage;
    preferred?: boolean;
}

interface IGeneralPractitioner {
    reference?: string;
    type?: string;
    identifier?: IIdentifier;
    display?: string;
}

interface IManagingOrganization {
    reference?: string;
    type?: string;
    identifier?: IIdentifier;
    display?: string;
}

interface IOther {
    reference?: string;
    type?: string;
    identifier?: IIdentifier;
    display?: string;
}

interface ILink {
    other?: IOther;
    type: string;
}

// Patient is an asset
export class Patient {
    public resourceType?: string;
    public id?: string;
    public meta?: IMeta;
    public implicitRules?: string;
    public language?: string;
    public Text?: Text;
    public identifier?: IIdentifier[];
    public active?: boolean;
    public name?: IName[];
    public telecom?: ITelecom[];
    public gender: string;
    public birthDate?: string;
    public deceasedBoolean?: boolean;
    public address?: IAddress[];
    public maritalStatus?: IMaritalStatus;
    public multipleBirthBoolean?: boolean;
    public photo?: IPhoto[];
    public contact?: IContact[];
    public communication?: ICommunication[];
    public generalPractitioner?: IGeneralPractitioner[];
    public managingOrganization?: IManagingOrganization[];
    public link?: ILink[];
}
