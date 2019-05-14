import { Selectable, Model, Selection, Eventer, Mix } from 'entcore-toolkit';
import http from "axios";
import { Shareable, Rights, notify, moment } from 'entcore';
import { Folders, Folder, Filters } from './folder';

export class Blog extends Model<Blog> implements Selectable, Shareable {
    static eventer = new Eventer();
    _id: string;
    selected: boolean;
    rights: Rights<Blog> = new Rights(this);
    owner: { userId: string, displayName: string };
    shared: any;
    trashed: boolean;
    title: string;
    shortenedTitle: string;
    visibility: 'PUBLIC' | 'PRIVATE';
    icon: string;
    modified: {
        $date: number
    };
    created: {
        $date: number
    };

    get lastModified(): string {
        return moment(this.modified.$date).format('DD/MM/YYYY');
    }
    constructor(data?: { _id: string, author: any, title: string, thumbnail: string }) {
        super({
            update: '/blog/:_id',
            sync: '/blog/:_id'
        });
        this.fromJSON(data);
        this.rights.fromBehaviours();
    }
    fromJSON(data) {
        if (data && data._id) {
            for (let i in data) {
                this[i] = data[i];
            }
            this._id = data._id;
            this.owner = data.author;
            this.shortenedTitle = data.title || '';
            if (this.shortenedTitle.length > 40) {
                this.shortenedTitle = this.shortenedTitle.substr(0, 38) + '...';
            }
            if (data.thumbnail) {
                this.icon = data.thumbnail + '?thumbnail=290x290';
            }
            else {
                this.icon = '/img/illustrations/blog.png';
            }
        }
    }
    get myRights() {
        return this.rights.myRights;
    }
    async save() {
        if (this._id) {
            await this.update();
        }
        else {
            await this.create();
        }
        Blog.eventer.trigger('save');
    }
    async  remove() {
        Folders.unprovide(this);
        await http.delete('/blog/' + this._id);
    }
    async toTrash() {
        this.trashed = true;
        await this.save();
        Folders.trash.sync();
        await this.save();
    }
    async moveTo(target: Folder | string) {
        await Folders.toRoot(this);
        if (target instanceof Folder && target._id) {
            target.ressourceIds.push(this._id);
            await target.sync();
        }
        else {
            await Folders.root.sync();
            if (target === 'trash') {
                await this.toTrash();
            }
        }
        await this.save();
    }
    copy(): Blog {
        let data = JSON.parse(JSON.stringify(this));
        data.published = undefined;
        data.title = "Copie_" + data.title;
        return Mix.castAs(Blog, data);
    }
}


export class Blogs {
    filtered: Blog[];
    sel: Selection<Blog>;

    get all(): Blog[] {
        return this.sel.all;
    }

    set all(list: Blog[]) {
        this.sel.all = list;
    }

    constructor() {
        this.sel = new Selection([]);
    }

    async fill(ids: string[]): Promise<void> {
        let blogs = await Folders.ressources();
        this.all = blogs.filter(
            w => ids.indexOf(w._id) !== -1 && !w.trashed
        );
        this.refreshFilters();
    }

    async duplicateSelection(): Promise<void> {
        for (let blog of this.sel.selected) {
            let copy = blog.copy();
            await copy.save();
            await copy.rights.fromBehaviours();
            this.all.push(copy);
            Folders.provideRessource(copy);
        }
        this.refreshFilters();
    }

    removeSelection() {
        this.sel.selected.forEach(function (blog) {
            blog.remove();
        });
        this.sel.removeSelection();
        notify.info('blog.removed');
    }

    async toTrash(): Promise<any> {
        for (let blog of this.all) {
            await blog.toTrash();
        }
    }

    removeBlog(blog: Blog) {
        let index = this.all.indexOf(blog);
        this.all.splice(index, 1);
    }

    refreshFilters() {
        this.filtered = this.all.filter(
            w => (w.visibility === 'PUBLIC' && Filters.public) || (w.visibility !== 'PUBLIC' && Filters.protected)
        );
    }

    deselectAll() {
        this.sel.deselectAll();
    }
}
