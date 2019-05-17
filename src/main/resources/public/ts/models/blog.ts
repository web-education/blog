import { Selectable, Model, Selection, Eventer, Mix } from 'entcore-toolkit';
import http from "axios";
import { Shareable, Rights, notify, moment, model } from 'entcore';
import { Folders, Folder, Filters } from './folder';
type BlogData = { _id: string, author: { userId: string, username: string }, title: string, thumbnail: string };
export class Blog extends Model<Blog> implements Selectable, Shareable {
    static eventer = new Eventer();
    _id: string;
    'comment-type': "RESTRAINT" | "IMMEDIATE" = "IMMEDIATE";
    'publish-type': "RESTRAINT" | "IMMEDIATE" = "RESTRAINT";
    selected: boolean;
    thumbnail: string;
    description: string;
    rights: Rights<Blog> = new Rights(this);
    owner: { userId: string, displayName: string };
    shared: any;
    trashed: boolean = false;
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
    author: { userId: string, username: string }
    get lastModified(): string {
        return moment(this.modified.$date).format('DD/MM/YYYY');
    }
    constructor(data?: BlogData) {
        super({
            create: '/blog',
            update: '/blog/:_id',
            sync: '/blog/:_id'
        });
        this.fromJSON(data);
    }
    async fromJSON(data: BlogData) {
        if (data) {
            for (let i in data) {
                this[i] = data[i];
            }
            this._id = data._id;
            this.owner = data.author as any;
            this.shortenedTitle = data.title || '';
            if (this.shortenedTitle.length > 40) {
                this.shortenedTitle = this.shortenedTitle.substr(0, 38) + '...';
            }
            this.icon = data.thumbnail ? data.thumbnail + '?thumbnail=290x290' : '';
        }
        this.rights.fromBehaviours();
    }
    get myRights() {
        return this.rights.myRights;
    }
    async save() {
        const json = this.toJSON();
        if (this._id) {
            await this.update(json as any);
        }
        else {
            const { trashed, ...others } = json
            const res = await this.create(others as any);
            //refresh
            this._id = res.data._id;
            this.owner = {
                userId: model.me.userId,
                displayName: model.me.username
            }
            this.modified = {
                $date: new Date().getTime()
            }
            this.author = {
                userId: model.me.userId,
                username: model.me.username
            }
            this.rights.fromBehaviours();
            //update root
            Folders.provideRessource(this);
            Folders.root.ressources.all.push(this);
            Folders.root.ressources.refreshFilters();
            Folder.eventer.trigger('refresh');
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

    toJSON() {
        const { trashed } = this;
        return {
            trashed,
            _id: this._id,
            title: this.title,
            thumbnail: this.thumbnail || '',
            'comment-type': this['comment-type'] || 'IMMEDIATE',
            'publish-type': this['publish-type'] || 'RESTRAINT',
            description: this.description || ''
        };
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
            w => {
                if (Filters.shared) {
                    return w.author.userId != model.me.userId;
                } else {
                    return w.author.userId == model.me.userId;
                }
            }
        );
    }

    deselectAll() {
        this.sel.deselectAll();
    }
}
