export class Validation {
    public executeDate: Date = new Date();
    public id: number;
    public age: number;
    public isValid: boolean;
    public toString(): string {
        return '{id: ' + this.id + '; executeDate: ' + this.executeDate + '; age: ' + this.age + '; isValid: ' + this.isValid + '}' ;
    }
}
